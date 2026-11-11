import * as assert from 'assert';
import {
  candidatePaths,
  controllerClassOf,
  findClass,
  findMethod,
  findProperty,
  propertyNames,
  targetAt,
} from '../navigation';

// Line numbers below are zero-based lines of this text.
const JAVA = [
  /*  0 */ 'package com.acme;',
  /*  1 */ '',
  /*  2 */ 'import java.util.List;',
  /*  3 */ '',
  /*  4 */ '/** Mentions save() and getName() in a comment. */',
  /*  5 */ 'public class CustomersController {',
  /*  6 */ '    private String status = "";',
  /*  7 */ '    private final List<String> cities;',
  /*  8 */ '',
  /*  9 */ '    public CustomersController(List<String> cities) {',
  /* 10 */ '        this.cities = cities;',
  /* 11 */ '    }',
  /* 12 */ '',
  /* 13 */ '    // save()',
  /* 14 */ '    public void save() {',
  /* 15 */ '        System.out.println(getName());',
  /* 16 */ '        return save();',
  /* 17 */ '    }',
  /* 18 */ '',
  /* 19 */ '    @Deprecated',
  /* 20 */ '    public static <T> List<T> open(T item) { return null; }',
  /* 21 */ '',
  /* 22 */ '    public String getName() { return name(); }',
  /* 23 */ '    public boolean isReadOnly() { return false; }',
  /* 24 */ '    public String city() { return ""; }',
  /* 25 */ '    public record Row(String id, int total) {}',
  /* 26 */ '    int[] counts() { return new int[0]; }',
  /* 27 */ '}',
  /* 28 */ '',
].join('\n');

describe('targetAt', () => {
  const line = '<Button text="Save ${s}" onAction="#save" disabled="${!readOnly}" onClick=\'#open(item, 2)\'/>';

  it('finds a handler name', () => {
    const start = line.indexOf('#save') + 1;
    assert.deepStrictEqual(targetAt(line, start + 1), { kind: 'handler', name: 'save', start, end: start + 4 });
  });

  it('finds a handler with arguments in single quotes, only on the name', () => {
    const name = line.indexOf('open');
    assert.strictEqual(targetAt(line, name + 2)?.name, 'open');
    assert.strictEqual(targetAt(line, line.indexOf('item')), undefined);
  });

  it('finds the first segment of a binding and skips the negation', () => {
    const t = targetAt(line, line.indexOf('readOnly') + 3);
    assert.strictEqual(t?.kind, 'property');
    assert.strictEqual(t?.name, 'readOnly');
  });

  it('takes the first segment of a path, not the later ones', () => {
    const l = '<Label text="${customer.name}"/>';
    assert.strictEqual(targetAt(l, l.indexOf('customer'))?.name, 'customer');
    assert.strictEqual(targetAt(l, l.indexOf('name')), undefined);
  });

  it('reads a two-way binding', () => {
    const l = '<TextField text="@{customer.name}"/>';
    assert.strictEqual(targetAt(l, l.indexOf('customer') + 1)?.name, 'customer');
  });

  it('is not fooled by a colour, a literal or text outside a value', () => {
    const l = '<Row background="#336699" text="\\${x} \\#1" onAction="nothing"/>';
    assert.strictEqual(targetAt(l, l.indexOf('336699')), undefined);
    assert.strictEqual(targetAt(l, l.indexOf('{x}') + 1), undefined);
    assert.strictEqual(targetAt(l, l.indexOf('nothing')), undefined);
    assert.strictEqual(targetAt(l, 2), undefined);
  });

  it('finds the controller class on the controller attribute', () => {
    const l = '<Column controller="com.acme.CustomersController" gap="8">';
    const t = targetAt(l, l.indexOf('Customers'));
    assert.deepStrictEqual([t?.kind, t?.name], ['controller', 'com.acme.CustomersController']);
  });
});

describe('controllerClassOf', () => {
  it('reads the root element', () => {
    const xml = '<?xml version="1.0"?>\n<Column xmlns:xsi="x"\n   controller="com.acme.A"><Row controller="no"/></Column>';
    assert.strictEqual(controllerClassOf(xml), 'com.acme.A');
  });

  it('skips comments and the prolog', () => {
    const xml = '<?xml version="1.0"?>\n<!-- <Fake controller="com.no.Fake"> -->\n<Border controller=\'com.acme.B\'/>';
    assert.strictEqual(controllerClassOf(xml), 'com.acme.B');
  });

  it('does not take a controller from a child when the root has none', () => {
    assert.strictEqual(controllerClassOf('<Column><Row controller="com.acme.C"/></Column>'), undefined);
  });

  it('is undefined without a root attribute list', () => {
    assert.strictEqual(controllerClassOf('<Column/>'), undefined);
    assert.strictEqual(controllerClassOf(''), undefined);
  });
});

describe('candidatePaths', () => {
  it('lists the top-level class first, then the classes that may enclose it', () => {
    assert.deepStrictEqual(candidatePaths('com.acme.Outer.Inner'), [
      'com/acme/Outer/Inner.java',
      'com/acme/Outer.java',
    ]);
  });

  it('handles a class in the default package', () => {
    assert.deepStrictEqual(candidatePaths('Main'), ['Main.java']);
  });
});

describe('findMethod', () => {
  it('finds a declaration and not a call or a comment', () => {
    assert.deepStrictEqual(findMethod(JAVA, 'save'), { line: 14, character: 16 });
  });

  it('finds generic and annotated methods', () => {
    const p = findMethod(JAVA, 'open');
    assert.strictEqual(p?.line, 20);
    assert.strictEqual(JAVA.split('\n')[20].substring(p!.character, p!.character + 4), 'open');
  });

  it('finds package-private methods with an array return type', () => {
    assert.strictEqual(findMethod(JAVA, 'counts')?.line, 26);
  });

  it('is undefined for a name that is only called', () => {
    assert.strictEqual(findMethod('class A {\n  void f() {\n    g();\n    int x = h(1);\n  }\n}', 'g'), undefined);
    assert.strictEqual(findMethod('class A {\n  void f() {\n    int x = h(1);\n  }\n}', 'h'), undefined);
  });
});

describe('findProperty', () => {
  it('prefers the getter', () => {
    assert.strictEqual(findProperty(JAVA, 'name')?.line, 22);
  });

  it('finds an is-getter', () => {
    assert.strictEqual(findProperty(JAVA, 'readOnly')?.line, 23);
  });

  it('finds a plain accessor', () => {
    assert.strictEqual(findProperty(JAVA, 'city')?.line, 24);
  });

  it('falls back to a field', () => {
    assert.deepStrictEqual(findProperty(JAVA, 'status'), { line: 6, character: 19 });
  });

  it('finds a record component', () => {
    const src = 'public record Customer(String name, int age) {}';
    assert.strictEqual(findProperty(src, 'age')?.character, src.indexOf('age'));
  });

  it('is undefined for an unknown property', () => {
    assert.strictEqual(findProperty(JAVA, 'nothing'), undefined);
  });

  it('names the candidates', () => {
    assert.deepStrictEqual(propertyNames('readOnly'), ['getReadOnly', 'isReadOnly', 'readOnly']);
  });
});

describe('findClass', () => {
  it('finds a class and a nested record', () => {
    assert.strictEqual(findClass(JAVA, 'CustomersController').line, 5);
    assert.strictEqual(findClass(JAVA, 'Row').line, 25);
  });

  it('falls back to the start of the file', () => {
    assert.deepStrictEqual(findClass('', 'X'), { line: 0, character: 0 });
  });
});
