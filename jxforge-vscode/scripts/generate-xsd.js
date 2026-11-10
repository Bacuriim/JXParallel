#!/usr/bin/env node
// Regenerates schemas/jxml.xsd from the JXForge tag table by running com.jxparallel.forge.JxmlXsd.
// Build the jars first, from the repository root (JDK 17):
//   mvn -o -q -pl jxparallel-forge -am package -DskipTests
// Then: npm run xsd
'use strict';
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const repo = path.resolve(__dirname, '..', '..');
const out = path.resolve(__dirname, '..', 'schemas', 'jxml.xsd');

function jar(module) {
  const dir = path.join(repo, module, 'target');
  const found = fs.existsSync(dir)
    ? fs.readdirSync(dir).filter((f) => f.startsWith(module + '-') && f.endsWith('.jar') && !/(sources|javadoc|tests)\.jar$/.test(f))
    : [];
  if (found.length === 0) {
    console.error(`No jar in ${dir}. Run: mvn -o -q -pl jxparallel-forge -am package -DskipTests`);
    process.exit(1);
  }
  return path.join(dir, found[0]);
}

const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const classpath = [jar('jxparallel-forge'), jar('jxparallel-core')].join(path.delimiter);
fs.mkdirSync(path.dirname(out), { recursive: true });
const result = spawnSync(java, ['-cp', classpath, 'com.jxparallel.forge.JxmlXsd', '--out', out], { stdio: 'inherit' });
process.exit(result.status === null ? 1 : result.status);
