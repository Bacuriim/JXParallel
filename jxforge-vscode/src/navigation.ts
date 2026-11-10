/**
 * The editor-independent part of "Go to Definition" for JXML: finding what is under the cursor, which controller class
 * the file names, which Java files may hold it, and where in that file the method or getter is declared. It does plain
 * text work (no Java parser, no language server), so it finds the declaration a reader would find with a text search
 * and nothing smarter. Nothing here imports `vscode`, so the unit tests run under plain mocha.
 */

/** What the cursor is on. */
export type Target =
  /** `onAction="#save"` or `#open(item)` with the cursor on the method name: a controller method. */
  | { kind: 'handler'; name: string; start: number; end: number }
  /** `${customer.name}` with the cursor on the first segment: a controller property (getter, accessor or field). */
  | { kind: 'property'; name: string; start: number; end: number }
  /** `controller="com.acme.Foo"`: the controller class itself. */
  | { kind: 'controller'; name: string; start: number; end: number };

const IDENT = '[\\p{L}_$][\\p{L}\\p{N}_$]*';

/**
 * The target at `character` of `line`, or undefined. A handler is the value of an `on*` attribute that starts with `#`;
 * a property is the first segment of a `${...}` or `@{...}` expression (the later segments belong to types this text
 * search does not know), and a leading `!` is skipped.
 */
export function targetAt(line: string, character: number): Target | undefined {
  const handler = new RegExp(`\\bon[A-Z]\\w*\\s*=\\s*(["'])\\s*#\\s*(${IDENT})`, 'gu');
  for (let m = handler.exec(line); m; m = handler.exec(line)) {
    const name = m[2];
    const end = m.index + m[0].length;
    const start = end - name.length;
    if (character >= start && character <= end) {
      return { kind: 'handler', name, start, end };
    }
  }

  const controller = new RegExp(`\\bcontroller\\s*=\\s*(["'])\\s*([\\p{L}_$][\\p{L}\\p{N}_$.]*)`, 'gu');
  for (let m = controller.exec(line); m; m = controller.exec(line)) {
    const name = m[2];
    const end = m.index + m[0].length;
    const start = end - name.length;
    if (character >= start && character <= end) {
      return { kind: 'controller', name, start, end };
    }
  }

  const binding = new RegExp(`(?<!\\\\)[$@]\\{\\s*!?\\s*(${IDENT})`, 'gu');
  for (let m = binding.exec(line); m; m = binding.exec(line)) {
    const name = m[1];
    const end = m.index + m[0].length;
    const start = end - name.length;
    if (character >= start && character <= end) {
      return { kind: 'property', name, start, end };
    }
  }
  return undefined;
}

/** The value of `controller="..."` on the root element, or undefined. Comments and the XML prolog are skipped. */
export function controllerClassOf(xml: string): string | undefined {
  const withoutComments = xml.replace(/<!--[\s\S]*?-->/g, (c) => c.replace(/[^\n]/g, ' '));
  const root = /<([A-Za-z_][\w.:-]*)(\s[^>]*)?>/.exec(withoutComments);
  if (!root || !root[2]) {
    return undefined;
  }
  const attr = /\bcontroller\s*=\s*(?:"([^"]*)"|'([^']*)')/.exec(root[2]);
  const value = attr && (attr[1] ?? attr[2]);
  return value && value.trim() ? value.trim() : undefined;
}

/**
 * Where the source of `className` may be, relative to a source root, most likely first: `a.b.Outer.Inner` is
 * `a/b/Outer/Inner.java` (a top-level class), or `a/b/Outer.java` (a nested class of `Outer`), or `a/Outer.java`.
 */
export function candidatePaths(className: string): string[] {
  const parts = className.split('.').filter((p) => p.length > 0);
  const paths: string[] = [];
  for (let n = parts.length; n >= 1; n--) {
    if (n < parts.length && !/^[A-Z]/.test(parts[n - 1])) {
      break; // a package segment, not an enclosing class
    }
    paths.push(parts.slice(0, n).join('/') + '.java');
  }
  return paths;
}

/** A position in a text, zero-based. */
export interface Position {
  line: number;
  character: number;
}

/** The names a controller property `name` can be declared under: `getName()`, `isName()`, `name()` or a field. */
export function propertyNames(name: string): string[] {
  const cap = name.charAt(0).toUpperCase() + name.slice(1);
  return ['get' + cap, 'is' + cap, name];
}

const MODIFIERS = '(?:(?:public|protected|private|static|final|abstract|synchronized|default|native)\\s+)*';
const NOT_A_DECLARATION = /^\s*(?:return|new|throw|else|if|for|while|switch|case|package|import|\/\/|\*|\/\*)\b|^\s*(?:\/\/|\*|\/\*)/;

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * The declaration of method `name` in Java `source`: a line with optional annotations and modifiers, a return type
 * and then `name(`. The first match wins; a call such as `this.name()` or `return name();` is not one. Returns the
 * position of the name.
 */
export function findMethod(source: string, name: string): Position | undefined {
  const re = new RegExp(
    `^\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s+)*${MODIFIERS}(?:<[^>]+>\\s+)?[\\w.$<>\\[\\]?,]+(?:\\s*\\[\\s*\\])*\\s+(${escapeRegExp(name)})\\s*\\(`,
  );
  const lines = source.split(/\r\n|\n|\r/);
  for (let i = 0; i < lines.length; i++) {
    if (NOT_A_DECLARATION.test(lines[i])) {
      continue;
    }
    const m = re.exec(lines[i]);
    if (m) {
      return { line: i, character: m.index + m[0].lastIndexOf(name) };
    }
  }
  return undefined;
}

/**
 * The declaration of a controller property `name` (what `${name}` reads): its getter, `isName`, an accessor `name()`
 * (a record component or a fluent method), then a field `name`, or a record component.
 */
export function findProperty(source: string, name: string): Position | undefined {
  for (const candidate of propertyNames(name)) {
    const found = findMethod(source, candidate);
    if (found) {
      return found;
    }
  }
  const field = new RegExp(
    `^\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s+)*${MODIFIERS}[\\w.$<>\\[\\]?,]+\\s+(${escapeRegExp(name)})\\s*(?:=|;)`,
  );
  const lines = source.split(/\r\n|\n|\r/);
  for (let i = 0; i < lines.length; i++) {
    if (NOT_A_DECLARATION.test(lines[i])) {
      continue;
    }
    const m = field.exec(lines[i]);
    if (m) {
      return { line: i, character: m.index + m[0].lastIndexOf(name) };
    }
  }
  const record = new RegExp(`\\brecord\\s+\\w+\\s*(?:<[^>]*>)?\\s*\\([^)]*?[\\w>\\]]\\s+(${escapeRegExp(name)})\\s*[,)]`);
  for (let i = 0; i < lines.length; i++) {
    const m = record.exec(lines[i]);
    if (m) {
      return { line: i, character: m.index + m[0].lastIndexOf(name) };
    }
  }
  return undefined;
}

/** The position of the declaration of the class `simpleName` (the last segment of a class name), or the file start. */
export function findClass(source: string, simpleName: string): Position {
  const re = new RegExp(`\\b(?:class|interface|enum|record)\\s+(${escapeRegExp(simpleName)})\\b`);
  const lines = source.split(/\r\n|\n|\r/);
  for (let i = 0; i < lines.length; i++) {
    const m = re.exec(lines[i]);
    if (m) {
      return { line: i, character: m.index + m[0].lastIndexOf(simpleName) };
    }
  }
  return { line: 0, character: 0 };
}
