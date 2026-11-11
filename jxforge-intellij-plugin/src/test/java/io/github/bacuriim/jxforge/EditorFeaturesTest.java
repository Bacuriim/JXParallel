package io.github.bacuriim.jxforge;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.indexing.FileBasedIndex;
import io.github.bacuriim.jxforge.gutter.JxmlControllerIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class EditorFeaturesTest extends JxmlJavaTestCase {
    // ---- highlighting -----------------------------------------------------------------------------------------

    private List<String> keyedRanges(String text) {
        myFixture.configureByText("a.jxml", text);
        List<String> result = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            TextAttributesKey key = info.forcedTextAttributesKey;
            if (key != null && key.getExternalName().startsWith("JXML_")) {
                result.add(key.getExternalName() + ":" + info.getText());
            }
        }
        return result;
    }

    public void testBindingsAndHandlersGetTheirOwnTextAttributes() {
        List<String> r = keyedRanges(screen("<Label text=\"${title}\"/><TextField text=\"@{title}\"/><Button onAction=\"#save\"/>"));
        assertTrue(r.toString(), r.contains("JXML_BINDING:${title}"));
        assertTrue(r.toString(), r.contains("JXML_TWO_WAY_BINDING:@{title}"));
        assertTrue(r.toString(), r.contains("JXML_HANDLER:#save"));
    }

    public void testPlainValuesAreNotHighlighted() {
        List<String> r = keyedRanges(screen("<Label text=\"plain\" background=\"#FFF\"/>"));
        assertEmpty(r);
    }

    public void testHandlerWithArgumentsIsHighlightedWhole() {
        List<String> r = keyedRanges(screen("<For items=\"${orders}\" var=\"o\"><Button onAction=\"#open(o)\"/></For>"));
        assertTrue(r.toString(), r.contains("JXML_HANDLER:#open(o)"));
        assertTrue(r.toString(), r.contains("JXML_BINDING:${orders}"));
    }

    public void testKeysHaveFallbackColorsSoTheyAreVisibleInEveryScheme() {
        for (TextAttributesKey key : new TextAttributesKey[]{JxmlHighlightKeys.BINDING, JxmlHighlightKeys.TWO_WAY_BINDING,
                JxmlHighlightKeys.HANDLER}) {
            assertNotNull(key.getFallbackAttributeKey());
        }
    }

    public void testColorSettingsPageListsTheKeys() {
        JxmlColorSettingsPage page = new JxmlColorSettingsPage();
        assertEquals("JXML", page.getDisplayName());
        Set<TextAttributesKey> keys = new java.util.HashSet<>();
        for (AttributesDescriptor d : page.getAttributeDescriptors()) {
            keys.add(d.getKey());
        }
        assertTrue(keys.containsAll(Set.of(JxmlHighlightKeys.BINDING, JxmlHighlightKeys.TWO_WAY_BINDING, JxmlHighlightKeys.HANDLER)));
        assertTrue(page.getAdditionalHighlightingTagToDescriptorMap().keySet().containsAll(Set.of("one", "two", "handler")));
        assertNotNull(page.getHighlighter());
        assertTrue(page.getDemoText().contains("<handler>"));
    }

    public void testColorSettingsPageIsRegistered() {
        boolean found = false;
        for (com.intellij.openapi.options.colors.ColorSettingsPage page
                : com.intellij.openapi.options.colors.ColorSettingsPage.EP_NAME.getExtensionList()) {
            found |= page instanceof JxmlColorSettingsPage;
        }
        assertTrue(found);
    }

    // ---- gutter -------------------------------------------------------------------------------------------------

    private List<LineMarkerInfo<?>> gutters() {
        myFixture.doHighlighting();
        return new ArrayList<LineMarkerInfo<?>>(DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.getEditor().getDocument(), getProject()));
    }

    private List<PsiElement> targets(LineMarkerInfo<?> mark) {
        List<PsiElement> out = new ArrayList<>();
        if (mark instanceof RelatedItemLineMarkerInfo) {
            ((RelatedItemLineMarkerInfo<?>) mark).createGotoRelatedItems().forEach(i -> out.add(i.getElement()));
        }
        return out;
    }

    public void testGutterOnTheRootTagNavigatesToTheController() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"x\"/>"));
        List<LineMarkerInfo<?>> marks = gutters();
        assertEquals(marks.toString(), 1, marks.size());
        List<PsiElement> targets = targets(marks.get(0));
        assertEquals(List.of(ctl()), targets);
        // the icon sits on the root tag name, line 1
        assertEquals(0, myFixture.getEditor().getDocument().getLineNumber(marks.get(0).startOffset));
    }

    public void testNoGutterWithoutAController() {
        myFixture.configureByText("a.jxml", "<Column><Label text=\"x\"/></Column>");
        assertEmpty(gutters());
        myFixture.configureByText("b.jxml", "<Column controller=\"com.acme.Missing\"/>");
        assertEmpty(gutters());
    }

    public void testGutterOnTheControllerClassNavigatesToTheScreens() {
        PsiFile one = myFixture.addFileToProject("screens/one.jxml", screen("<Label text=\"1\"/>"));
        PsiFile two = myFixture.addFileToProject("screens/two.jxml", screen("<Label text=\"2\"/>"));
        myFixture.addFileToProject("screens/other.jxml", "<Column controller=\"com.acme.Other\"/>");
        myFixture.configureFromExistingVirtualFile(ctl().getContainingFile().getVirtualFile());
        List<LineMarkerInfo<?>> marks = gutters();
        List<PsiElement> targets = new ArrayList<>();
        for (LineMarkerInfo<?> m : marks) {
            targets.addAll(targets(m));
        }
        assertEquals(targets.toString(), Set.of(one, two), new java.util.HashSet<>(targets));
    }

    public void testNoGutterOnAClassNoScreenUses() {
        myFixture.addFileToProject("screens/one.jxml", screen("<Label text=\"1\"/>"));
        myFixture.configureFromExistingVirtualFile(myFixture.findClass("com.acme.Customer").getContainingFile().getVirtualFile());
        assertEmpty(gutters());
    }

    public void testControllerIndexMapsClassNamesToFiles() {
        PsiFile one = myFixture.addFileToProject("screens/one.jxml", screen("<Label text=\"1\"/>"));
        assertEquals(List.of(one), JxmlControllerIndex.filesOf(getProject(), "com.acme.Ctl"));
        assertEmpty(JxmlControllerIndex.filesOf(getProject(), "com.acme.Nothing"));
        assertTrue(FileBasedIndex.getInstance().getAllKeys(JxmlControllerIndex.NAME, getProject()).contains("com.acme.Ctl"));
    }

    // ---- file template ------------------------------------------------------------------------------------------

    public void testJxmlScreenTemplateExistsAndCreatesAValidScreen() throws Exception {
        FileTemplate template = FileTemplateManager.getInstance(getProject()).getInternalTemplate("JXML Screen");
        assertNotNull(template);
        assertEquals("jxml", template.getExtension());
        PsiDirectory dir = PsiManager.getInstance(getProject()).findDirectory(myFixture.getTempDirFixture().findOrCreateDir("screens"));
        Properties props = new Properties(FileTemplateManager.getInstance(getProject()).getDefaultProperties());
        com.intellij.openapi.application.WriteAction.runAndWait(() -> {
            try {
                PsiElement created = FileTemplateUtil.createFromTemplate(template, "Orders", props, dir);
                assertEquals("Orders.jxml", ((PsiFile) created).getName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        PsiFile file = dir.findFile("Orders.jxml");
        assertNotNull(file);
        assertSame(JxmlFileType.INSTANCE, file.getFileType());
        XmlFile xml = (XmlFile) file;
        assertEquals("Column", xml.getRootTag().getName());
        assertEquals("screens.OrdersController", xml.getRootTag().getAttributeValue("controller"));
        assertTrue(file.getText(), file.getText().contains("xsi:noNamespaceSchemaLocation=\"urn:jxforge:jxml.xsd\""));
        assertFalse(file.getText(), file.getText().contains("${CONTROLLER"));
        // the generated screen is valid against the bundled schema (only the missing controller class is reported)
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        List<HighlightInfo> errors = myFixture.doHighlighting().stream()
                .filter(i -> i.getSeverity() == HighlightSeverity.ERROR).collect(Collectors.toList());
        assertEquals(describe(errors), 1, errors.size());
        assertTrue(describe(errors), describe(errors).contains("Cannot resolve class"));
    }

    public void testNewJxmlScreenActionIsRegisteredInTheNewGroup() {
        com.intellij.openapi.actionSystem.AnAction action = com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("JXForge.NewJxmlScreen");
        assertNotNull(action);
        assertEquals("JXML Screen", action.getTemplatePresentation().getText());
        com.intellij.openapi.actionSystem.DefaultActionGroup group = (com.intellij.openapi.actionSystem.DefaultActionGroup)
                com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("NewGroup");
        assertTrue(java.util.Arrays.asList(group.getChildren((com.intellij.openapi.actionSystem.AnActionEvent) null)).contains(action));
    }
}
