"""Points an application's imports at the JavaFX-compatible API of JXParallel.

    python scripts/migrate-imports.py <source dir>...

Each import of javafx.X, com.sun.javafx.X or org.controlsfx.X (single, wildcard or static) becomes
com.jxparallel.fx.X, com.jxparallel.fx.sun.X or com.jxparallel.fx.controlsfx.X when jxparallel-fx
provides that class or package; other imports are left alone and listed at the end. Only import
lines change and every other byte is kept, so each file keeps its encoding. Prints the changed files.
"""
import os
import re
import sys
from collections import Counter

FX_ROOTS = [os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', module, 'src', 'main', 'java')
            for module in ('jxparallel-fx', 'jxparallel-fx-controlsfx')]
PREFIXES = [('com.sun.javafx.', 'com.jxparallel.fx.sun.'),
            ('org.controlsfx.', 'com.jxparallel.fx.controlsfx.'),
            ('javafx.', 'com.jxparallel.fx.')]
IMPORT = re.compile(r'^import(\s+static)?\s+([\w.]+?)(\.\*)?\s*;', re.M)
QUALIFIED = re.compile(r'(?<![\w."])(?:javafx|com\.sun\.javafx|org\.controlsfx)(?:\.[a-z]\w*)+\.[A-Z]\w*')


def provided(name, wildcard):
    """Whether jxparallel-fx has the class (or, for a wildcard, the package) of this JX name."""
    parts = name.split('.')
    for root in FX_ROOTS:
        if wildcard and os.path.isdir(os.path.join(root, *parts)):
            return True
        # a class, a nested class (Alert.AlertType) or a static member (Pos.CENTER): some prefix is a file
        for end in range(len(parts), 3, -1):
            if os.path.isfile(os.path.join(root, *parts[:end]) + '.java'):
                return True
    return False


def counterpart(name):
    for fx, jx in PREFIXES:
        if name.startswith(fx):
            return jx + name[len(fx):]
    return None


def migrate(text, missing):
    def replace(m):
        static, name, star = m.group(1) or '', m.group(2), m.group(3) or ''
        jx = counterpart(name)
        if jx is None:
            return m.group(0)
        if provided(jx, bool(star) and not static):
            return 'import' + static + ' ' + jx + star + ';'
        missing[name + star] += 1
        return m.group(0)

    text = IMPORT.sub(replace, text)

    # A fully qualified name in code (new javafx.scene.image.Image(...)) is an inline import.
    def qualified(m):
        jx = counterpart(m.group(0))
        if jx is not None and provided(jx, False):
            return jx
        missing[m.group(0) + ' (qualified)'] += 1
        return m.group(0)

    lines = text.split('\n')
    for i, line in enumerate(lines):
        if not line.lstrip().startswith(('import ', 'package ', '//', '*', '/*')):
            lines[i] = QUALIFIED.sub(qualified, line)
    return '\n'.join(lines)


def main(roots):
    missing = Counter()
    for root in roots:
        for d, dirs, files in os.walk(root):
            dirs[:] = [x for x in dirs if x not in ('target', '.git')]
            for f in files:
                if not f.endswith('.java'):
                    continue
                path = os.path.join(d, f)
                with open(path, 'rb') as fh:
                    original = fh.read().decode('latin-1')
                changed = migrate(original, missing)
                if changed != original:
                    with open(path, 'wb') as fh:
                        fh.write(changed.encode('latin-1'))
                    print(path)
    for name, count in missing.most_common():
        print('NOT PROVIDED %5d %s' % (count, name), file=sys.stderr)


if __name__ == '__main__':
    main(sys.argv[1:])
