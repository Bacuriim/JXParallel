import * as vscode from 'vscode';
import { candidatePaths, controllerClassOf, findClass, findMethod, findProperty, targetAt } from './navigation';

const EXCLUDE = '**/{node_modules,target,build,out,.git}/**';

/**
 * Go to Definition for JXML: a `#handler` goes to the controller method, the first segment of a `${...}` or `@{...}`
 * to the getter (or accessor, or field), and `controller="..."` to the class. The controller is the class named by
 * `controller="..."` on the root element; its file is found in the workspace by name and read as text.
 */
export class JxmlDefinitionProvider implements vscode.DefinitionProvider {
  async provideDefinition(document: vscode.TextDocument, position: vscode.Position): Promise<vscode.Location | undefined> {
    const target = targetAt(document.lineAt(position.line).text, position.character);
    if (!target) {
      return undefined;
    }
    const className = target.kind === 'controller' ? target.name : controllerClassOf(document.getText());
    if (!className) {
      return undefined;
    }
    const file = await findJavaFile(className);
    if (!file) {
      return undefined;
    }
    const source = Buffer.from(await vscode.workspace.fs.readFile(file)).toString('utf8');
    let found;
    if (target.kind === 'handler') {
      found = findMethod(source, target.name);
    } else if (target.kind === 'property') {
      found = findProperty(source, target.name);
    } else {
      found = findClass(source, className.substring(className.lastIndexOf('.') + 1));
    }
    return found ? new vscode.Location(file, new vscode.Position(found.line, found.character)) : undefined;
  }
}

async function findJavaFile(className: string): Promise<vscode.Uri | undefined> {
  for (const path of candidatePaths(className)) {
    const files = await vscode.workspace.findFiles(`**/${path}`, EXCLUDE, 1);
    if (files.length > 0) {
      return files[0];
    }
  }
  return undefined;
}
