import * as assert from 'assert';
import * as fs from 'fs';
import * as path from 'path';

// out/test/manifest.test.js -> the extension folder is two levels up.
const root = path.resolve(__dirname, '..', '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const contributes = manifest.contributes;

function exists(relative: string): boolean {
  return fs.existsSync(path.join(root, relative));
}

describe('manifest', () => {
  it('is published by bacuriim', () => {
    assert.strictEqual(manifest.publisher, 'bacuriim');
  });

  it('declares the jxml language for .jxml with its icons and configuration', () => {
    const language = contributes.languages.find((l: { id: string }) => l.id === 'jxml');
    assert.ok(language, 'language jxml');
    assert.deepStrictEqual(language.extensions, ['.jxml']);
    assert.ok(language.aliases.includes('JXML'));
    for (const file of [language.configuration, language.icon.light, language.icon.dark]) {
      assert.ok(exists(file), file);
    }
  });

  it('asks the XML extension to treat jxml as XML', () => {
    assert.deepStrictEqual(contributes.xmlLanguageParticipants, [{ languageId: 'jxml' }]);
    assert.ok(manifest.extensionPack.includes('redhat.vscode-xml'));
  });

  it('points to files that exist', () => {
    for (const grammar of contributes.grammars) {
      assert.ok(exists(grammar.path), grammar.path);
    }
    for (const snippets of contributes.snippets) {
      assert.ok(exists(snippets.path), snippets.path);
    }
    assert.ok(exists(manifest.main.replace('./', '')), manifest.main);
  });
});

describe('grammar', () => {
  const grammar = JSON.parse(fs.readFileSync(path.join(root, 'syntaxes', 'jxml.tmLanguage.json'), 'utf8'));

  it('includes the XML grammar and injects into its scopes', () => {
    assert.strictEqual(grammar.scopeName, 'text.xml.jxml');
    assert.deepStrictEqual(grammar.patterns, [{ include: 'text.xml' }]);
    assert.ok(Object.keys(grammar.injections).length >= 2);
  });

  it('compiles every pattern as a regular expression', () => {
    // vscode-textmate uses Oniguruma; \p{L} is the one syntax JavaScript needs the u flag for.
    const regexes: string[] = [];
    JSON.stringify(grammar, (key, value) => {
      if ((key === 'begin' || key === 'end' || key === 'match') && typeof value === 'string') {
        regexes.push(value);
      }
      return value;
    });
    assert.ok(regexes.length > 5);
    for (const source of regexes) {
      assert.doesNotThrow(() => new RegExp(source, 'u'), source);
    }
  });
});

describe('snippets', () => {
  const snippets = JSON.parse(fs.readFileSync(path.join(root, 'snippets', 'jxml.json'), 'utf8'));

  it('has the screen, button, text field, for and dialog snippets', () => {
    for (const name of ['JXML screen', 'Button with handler', 'TextField with two-way binding', 'For loop', 'Modal dialog']) {
      assert.ok(snippets[name], name);
      assert.ok(snippets[name].prefix.length > 0 && snippets[name].body.length > 0, name);
    }
  });

  it('writes the controller and the schema line in the screen snippet', () => {
    const body = snippets['JXML screen'].body.join('\n');
    assert.match(body, /controller="/);
    assert.match(body, /xsi:noNamespaceSchemaLocation=/);
  });

  it('writes the two-way binding and the handler as JXML spells them', () => {
    assert.match(snippets['TextField with two-way binding'].body.join('\n'), /text="@\{\$\{1:name\}\}"/);
    assert.match(snippets['Button with handler'].body.join('\n'), /onAction="#\$\{2:save\}"/);
  });
});

describe('schema', () => {
  const xsd = fs.readFileSync(path.join(root, 'schemas', 'jxml.xsd'), 'utf8');

  it('is the JxmlXsd output', () => {
    assert.match(xsd, /^<\?xml version="1.0" encoding="UTF-8"\?>\s*<!-- Generated from JxmlSchema by com\.jxparallel\.forge\.JxmlXsd/);
    for (const tag of ['Column', 'Button', 'TextField', 'For', 'If', 'Include', 'Style', 'Dialog']) {
      assert.ok(xsd.includes(`<xs:element name="${tag}"`), tag);
    }
  });

  it('has no namespace, as JXML files have none', () => {
    assert.ok(!/targetNamespace=/.test(xsd));
  });
});
