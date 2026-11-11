import * as vscode from 'vscode';
import { JxmlDefinitionProvider } from './definition';

const XML_EXTENSION = 'redhat.vscode-xml';

/** The part of the API of `redhat.vscode-xml` that is used (see src/api/xmlExtensionApi.ts in its repository). */
interface XmlExtensionApi {
  addXMLFileAssociations(fileAssociations: { systemId: string; pattern: string }[]): void;
  removeXMLFileAssociations(fileAssociations: { systemId: string; pattern: string }[]): void;
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  context.subscriptions.push(
    vscode.languages.registerDefinitionProvider({ language: 'jxml' }, new JxmlDefinitionProvider()),
  );
  await registerSchema(context);
}

export function deactivate(): void {
  // Nothing to release: the registrations are disposed through the context.
}

/**
 * Associates the bundled `jxml.xsd` with every `.jxml` file in the XML extension, so completion, hover and validation
 * work without a setting. The XML extension has no package.json contribution point for a schema (only
 * `xmlLanguageParticipants` for the language), but it exposes `addXMLFileAssociations` for this.
 */
async function registerSchema(context: vscode.ExtensionContext): Promise<void> {
  const xml = vscode.extensions.getExtension<XmlExtensionApi>(XML_EXTENSION);
  if (!xml) {
    const install = 'Install';
    void vscode.window
      .showInformationMessage(
        'JXML completion and validation need the XML extension from Red Hat; highlighting and snippets work without it.',
        install,
      )
      .then((choice) => {
        if (choice === install) {
          void vscode.commands.executeCommand('workbench.extensions.installExtension', XML_EXTENSION);
        }
      });
    return;
  }
  const api = await xml.activate();
  const association = {
    pattern: '**/*.jxml',
    systemId: vscode.Uri.joinPath(context.extensionUri, 'schemas', 'jxml.xsd').toString(),
  };
  api.addXMLFileAssociations([association]);
  context.subscriptions.push({ dispose: () => api.removeXMLFileAssociations([association]) });
}
