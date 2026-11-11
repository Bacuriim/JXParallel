package io.github.bacuriim.jxforge;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ControllerReferenceTest extends JxmlJavaTestCase {
    private List<String> complete(String text) {
        myFixture.configureByText("a.jxml", text);
        LookupElement[] items = myFixture.complete(CompletionType.BASIC);
        return items == null ? List.of() : Arrays.stream(items).map(LookupElement::getLookupString).collect(Collectors.toList());
    }

    // ---- controller="..." ------------------------------------------------------------------------------------

    public void testControllerResolvesToTheJavaClass() {
        myFixture.configureByText("a.jxml", "<Column controller=\"com.acme.C<caret>tl\"/>");
        assertSame(ctl(), resolveAtCaret());
    }

    public void testNestedControllerClassHasNoError() {
        myFixture.addClass("package com.acme; public class Outer { public static class Inner { public void go() { } } }");
        myFixture.configureByText("a.jxml", "<Column controller=\"com.acme.Outer.Inner\"><Button text=\"x\" onAction=\"#go\"/></Column>");
        assertEmpty(describe(highlights(HighlightSeverity.ERROR)), highlights(HighlightSeverity.ERROR));
    }

    public void testUnresolvedControllerIsAnError() {
        myFixture.configureByText("a.jxml", "<Column controller=\"com.acme.Missing\"/>");
        String all = describe(highlights(HighlightSeverity.ERROR));
        assertTrue(all, all.contains("Missing"));
    }

    public void testResolvedControllerHasNoError() {
        myFixture.configureByText("a.jxml", "<Column controller=\"com.acme.Ctl\"/>");
        assertEmpty(describe(highlights(HighlightSeverity.ERROR)), highlights(HighlightSeverity.ERROR));
    }

    public void testControllerClassNamesAreCompleted() {
        List<String> c = complete("<Column controller=\"com.acme.<caret>\"/>");
        assertTrue(c.toString(), c.contains("Ctl"));
        assertTrue(c.toString(), c.contains("Customer"));
    }

    // ---- #handler ---------------------------------------------------------------------------------------------

    public void testHandlerResolvesToTheControllerMethod() {
        myFixture.configureByText("a.jxml", screen("<Button text=\"x\" onAction=\"#sa<caret>ve\"/>"));
        assertSame(method("com.acme.Ctl", "save"), resolveAtCaret());
    }

    public void testHandlerWithArgumentResolves() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\">"
                + "<Button text=\"x\" onAction=\"#op<caret>en(o)\"/></For>"));
        assertSame(method("com.acme.Ctl", "open"), resolveAtCaret());
    }

    public void testHandlerArgumentResolvesToTheLoopVariable() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\">"
                + "<Button text=\"x\" onAction=\"#open(<caret>o)\"/></For>"));
        PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("o", ((XmlAttributeValue) target).getValue());
    }

    public void testHandlerCompletionListsPublicMethods() {
        List<String> c = complete(screen("<Button text=\"x\" onAction=\"#<caret>\"/>"));
        assertTrue(c.toString(), c.containsAll(List.of("save", "open", "rename", "setTitle")));
        assertFalse(c.toString(), c.contains("hidden"));
        assertFalse(c.toString(), c.contains("wait"));
    }

    public void testHandlerCompletionFiltersByPrefix() {
        myFixture.configureByText("a.jxml", screen("<Button text=\"x\" onAction=\"#sa<caret>\"/>"));
        LookupElement[] items = myFixture.complete(CompletionType.BASIC);
        assertNotNull(items);
        List<String> names = Arrays.stream(items).map(LookupElement::getLookupString).collect(Collectors.toList());
        assertTrue(names.toString(), names.contains("save"));
        assertFalse(names.toString(), names.contains("open"));
    }

    public void testUnresolvedHandlerIsAnError() {
        myFixture.configureByText("a.jxml", screen("<Button text=\"x\" onAction=\"#nothing\"/>"));
        List<HighlightInfo> errors = highlights(HighlightSeverity.ERROR);
        String all = describe(errors);
        assertTrue(all, all.contains("Cannot resolve method 'nothing' in 'Ctl'"));
    }

    public void testPrivateMethodIsNotAHandler() {
        myFixture.configureByText("a.jxml", screen("<Button text=\"x\" onAction=\"#hidden\"/>"));
        assertFalse(describe(highlights(HighlightSeverity.ERROR)), highlights(HighlightSeverity.ERROR).isEmpty());
    }

    public void testQuickFixCreatesTheMethodInTheController() {
        myFixture.configureByText("a.jxml", screen("<Button text=\"x\" onAction=\"#arch<caret>ive\"/>"));
        myFixture.doHighlighting();
        IntentionAction fix = myFixture.findSingleIntention("Create method 'archive' in Controller");
        myFixture.launchAction(fix);
        String source = ctl().getContainingFile().getText();
        assertTrue(source, source.contains("public void archive()"));
    }

    public void testQuickFixPassesTheLoopVariableWithItsType() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\">"
                + "<Button text=\"x\" onAction=\"#del<caret>ete(o)\"/></For>"));
        myFixture.doHighlighting();
        myFixture.launchAction(myFixture.findSingleIntention("Create method 'delete' in Controller"));
        String source = ctl().getContainingFile().getText();
        assertTrue(source, source.contains("public void delete(com.acme.Order o)") || source.contains("public void delete(Order o)"));
    }

    public void testHexColorOnANonHandlerAttributeIsNotAHandler() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"x\" background=\"#add\"/>"));
        assertFalse(describe(highlights(HighlightSeverity.ERROR)), describe(highlights(HighlightSeverity.ERROR)).contains("Cannot resolve method"));
    }

    // ---- ${a.b.c} and @{x} ------------------------------------------------------------------------------------

    public void testBindingSegmentResolvesToTheGetter() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${ti<caret>tle}\"/>"));
        assertSame(method("com.acme.Ctl", "getTitle"), resolveAtCaret());
    }

    public void testBooleanIsGetterResolvesWithAWeakWarning() {
        myFixture.configureByText("a.jxml", screen("<If test=\"${ad<caret>min}\"><Label text=\"x\"/></If>"));
        assertSame(method("com.acme.Ctl", "isAdmin"), resolveAtCaret());
        String all = describe(highlights(HighlightSeverity.WEAK_WARNING));
        assertTrue(all, all.contains("getAdmin"));
    }

    public void testNegatedConditionResolves() {
        myFixture.configureByText("a.jxml", screen("<If test=\"${!ad<caret>min}\"><Label text=\"x\"/></If>"));
        assertSame(method("com.acme.Ctl", "isAdmin"), resolveAtCaret());
    }

    public void testPathResolvesEachSegmentByType() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${customer.address.ci<caret>ty}\"/>"));
        assertSame(method("com.acme.Address", "getCity"), resolveAtCaret());
    }

    public void testMiddleSegmentResolves() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${customer.addr<caret>ess.city}\"/>"));
        assertSame(method("com.acme.Customer", "getAddress"), resolveAtCaret());
    }

    public void testRecordAccessorWrittenWithParentheses() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${current.na<caret>me()}\"/>"));
        PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("name", ((PsiMethod) target).getName());
        assertEmpty(describe(highlights(HighlightSeverity.WARNING)), highlights(HighlightSeverity.WARNING));
    }

    public void testRecordMethodWithParentheses() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${current.la<caret>bel()}\"/>"));
        assertEquals("label", ((PsiMethod) resolveAtCaret()).getName());
    }

    public void testRecordComponentWithoutParenthesesIsWeak() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${current.name}\"/>"));
        String all = describe(highlights(HighlightSeverity.WEAK_WARNING));
        assertTrue(all, all.contains("getName"));
    }

    public void testLoopVariableTypedByTheGenericsOfTheList() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\"><Label text=\"${o.na<caret>me()}\"/></For>"));
        PsiElement target = resolveAtCaret();
        assertEquals("name", ((PsiMethod) target).getName());
        assertEquals("com.acme.Order", ((PsiMethod) target).getContainingClass().getQualifiedName());
    }

    public void testLoopVariableTypedByAnArray() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orderArray}\" var=\"o\"><Label text=\"${o.to<caret>tal()}\"/></For>"));
        assertEquals("total", ((PsiMethod) resolveAtCaret()).getName());
    }

    public void testLoopVariableTypedByTheTypeAttribute() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${unknownList}\" var=\"c\" type=\"com.acme.Customer\">"
                + "<Label text=\"${c.em<caret>ail}\"/></For>"));
        assertSame(method("com.acme.Customer", "getEmail"), resolveAtCaret());
    }

    public void testLoopVariableItselfResolvesToItsDeclaration() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\"><Label text=\"${<caret>o}\"/></For>"));
        PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
    }

    public void testNestedLoopsUseTheNearestVariable() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\">"
                + "<For items=\"${customer}\" var=\"o\" type=\"com.acme.Customer\"><Label text=\"${o.em<caret>ail}\"/></For></For>"));
        assertSame(method("com.acme.Customer", "getEmail"), resolveAtCaret());
    }

    public void testListViewDeclaresALoopVariableToo() {
        myFixture.configureByText("a.jxml", screen("<ListView items=\"${orders}\" var=\"o\" type=\"com.acme.Order\">"
                + "<ListItem text=\"${o.na<caret>me}\"/></ListView>"));
        assertEquals("name", ((PsiMethod) resolveAtCaret()).getName());
    }

    public void testLoopVariableOutOfScopeIsNotResolved() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\"/><Label text=\"${o.name()}\"/>"));
        String all = describe(highlights(HighlightSeverity.WARNING));
        assertTrue(all, all.contains("Cannot resolve property 'o'"));
    }

    public void testUnresolvedSegmentIsAWarning() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${nothing}\"/><Label text=\"${customer.nope}\"/>"));
        String all = describe(highlights(HighlightSeverity.WARNING));
        assertTrue(all, all.contains("'nothing' Cannot resolve property 'nothing' in 'Ctl'"));
        assertTrue(all, all.contains("'nope' Cannot resolve property 'nope' in 'Customer'"));
        assertEmpty(describe(highlights(HighlightSeverity.ERROR)), highlights(HighlightSeverity.ERROR));
    }

    public void testUnknownTypeSaysNothing() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${unknownList}\" var=\"c\"><Label text=\"${c.whatever}\"/></For>"));
        String all = describe(highlights(HighlightSeverity.WARNING));
        assertFalse(all, all.contains("whatever"));
    }

    public void testBindingCompletionInTheControllerProperties() {
        List<String> c = complete(screen("<Label text=\"${<caret>}\"/>"));
        assertTrue(c.toString(), c.containsAll(List.of("title", "readOnly", "admin", "customer", "orders")));
        assertFalse(c.toString(), c.contains("class"));
    }

    public void testBindingCompletionAfterADot() {
        List<String> c = complete(screen("<Label text=\"${customer.<caret>}\"/>"));
        assertTrue(c.toString(), c.containsAll(List.of("name", "email", "address")));
        assertFalse(c.toString(), c.contains("title"));
    }

    public void testBindingCompletionOffersLoopVariablesAndRecordComponents() {
        List<String> c = complete(screen("<For items=\"${orders}\" var=\"o\"><Label text=\"${<caret>}\"/></For>"));
        assertTrue(c.toString(), c.contains("o"));
        c = complete(screen("<For items=\"${orders}\" var=\"o\"><Label text=\"${o.<caret>}\"/></For>"));
        assertTrue(c.toString(), c.containsAll(List.of("name", "total")));
    }

    public void testBindingCompletionWithoutClosingBraceWhileTyping() {
        List<String> c = complete(screen("<Label text=\"${cust<caret>\"/>"));
        assertTrue(c.toString(), c.isEmpty() || c.contains("customer")); // a single match is inserted
    }

    public void testTwoWayBindingResolvesToTheGetter() {
        myFixture.configureByText("a.jxml", screen("<TextField text=\"@{ti<caret>tle}\"/>"));
        assertSame(method("com.acme.Ctl", "getTitle"), resolveAtCaret());
        assertEmpty(describe(highlights(HighlightSeverity.ERROR)), highlights(HighlightSeverity.ERROR));
    }

    public void testTwoWayBindingThroughAPathChecksTheSetterOfTheLastOwner() {
        myFixture.configureByText("a.jxml", screen("<TextField text=\"@{customer.name}\"/>"));
        assertEmpty(describe(highlights(HighlightSeverity.ERROR)), highlights(HighlightSeverity.ERROR));
        myFixture.configureByText("b.jxml", screen("<TextField text=\"@{customer.email}\"/>"));
        String all = describe(highlights(HighlightSeverity.ERROR));
        assertTrue(all, all.contains("cannot be bound two ways") && all.contains("setEmail"));
    }

    public void testTwoWayBindingWithoutSetterIsAnError() {
        myFixture.configureByText("a.jxml", screen("<TextField text=\"@{readOnly}\"/>"));
        String all = describe(highlights(HighlightSeverity.ERROR));
        assertTrue(all, all.contains("'@{readOnly}' cannot be bound two ways") && all.contains("setReadOnly"));
    }

    public void testTwoWayBindingOfARecordAccessorIsAnError() {
        myFixture.configureByText("a.jxml", screen("<TextField text=\"@{current.name()}\"/>"));
        String all = describe(highlights(HighlightSeverity.ERROR));
        assertTrue(all, all.contains("record accessor"));
    }

    public void testTwoWayBindingOnALoopVariableItselfIsAnError() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\"><TextField text=\"@{o}\"/></For>"));
        assertTrue(describe(highlights(HighlightSeverity.ERROR)), describe(highlights(HighlightSeverity.ERROR)).contains("nothing to set"));
    }

    // ---- Include ------------------------------------------------------------------------------------------------

    public void testIncludeResolvesToTheFile() {
        PsiFile part = myFixture.addFileToProject("parts/header.jxml", "<Label text=\"h\"/>");
        myFixture.configureByText("main.jxml", screen("<Include src=\"parts/he<caret>ader.jxml\"/>"));
        PsiElement target = resolveAtCaret();
        assertEquals(part.getVirtualFile(), ((PsiFile) target).getVirtualFile());
    }

    public void testMissingIncludeIsAnError() {
        myFixture.configureByText("main.jxml", screen("<Include src=\"parts/none.jxml\"/>"));
        assertTrue(describe(highlights(HighlightSeverity.ERROR)), describe(highlights(HighlightSeverity.ERROR)).contains("none.jxml"));
    }

    public void testPartWithoutControllerUsesTheControllerOfTheIncludingFile() {
        myFixture.addFileToProject("parts/h.jxml", "<Label text=\"${ti<caret>tle}\"/>");
        myFixture.addFileToProject("main.jxml", screen("<Include src=\"parts/h.jxml\"/>"));
        myFixture.configureFromTempProjectFile("parts/h.jxml");
        assertSame(method("com.acme.Ctl", "getTitle"), resolveAtCaret());
    }

    // ---- Find Usages and rename ---------------------------------------------------------------------------------

    public void testFindUsagesOfAHandlerAndAGetter() {
        myFixture.configureByText("a.jxml", screen("<Button text=\"x\" onAction=\"#save\"/><Label text=\"${title}\"/>"));
        assertEquals(1, com.intellij.psi.search.searches.ReferencesSearch.search(method("com.acme.Ctl", "save")).findAll().size());
        assertEquals(1, com.intellij.psi.search.searches.ReferencesSearch.search(method("com.acme.Ctl", "getTitle")).findAll().size());
    }

    public void testRenameOfAMethodUpdatesTheHandlers() {
        myFixture.configureByText("a.jxml", screen("<Button text=\"x\" onAction=\"#save\"/><Button text=\"y\" onAction=\"#save\"/>"));
        myFixture.renameElement(method("com.acme.Ctl", "save"), "persist");
        assertEquals(screen("<Button text=\"x\" onAction=\"#persist\"/><Button text=\"y\" onAction=\"#persist\"/>"),
                myFixture.getEditor().getDocument().getText());
    }

    public void testRenameOfAMethodKeepsTheArguments() {
        myFixture.configureByText("a.jxml", screen("<For items=\"${orders}\" var=\"o\"><Button text=\"x\" onAction=\"#open(o)\"/></For>"));
        myFixture.renameElement(method("com.acme.Ctl", "open"), "show");
        assertTrue(myFixture.getEditor().getDocument().getText().contains("onAction=\"#show(o)\""));
    }

    public void testRenameOfAGetterUpdatesTheBindings() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${customer.name}\"/><TextField text=\"@{customer.name}\"/>"));
        myFixture.renameElement(method("com.acme.Customer", "getName"), "getFullName");
        assertEquals(screen("<Label text=\"${customer.fullName}\"/><TextField text=\"@{customer.fullName}\"/>"),
                myFixture.getEditor().getDocument().getText());
    }

    public void testRenameOfARootGetterUpdatesTheBinding() {
        myFixture.configureByText("a.jxml", screen("<Label text=\"${title}\"/>"));
        myFixture.renameElement(method("com.acme.Ctl", "getTitle"), "getHeading");
        assertTrue(myFixture.getEditor().getDocument().getText().contains("${heading}"));
    }

    public void testRenameOfAControllerClassUpdatesTheAttribute() {
        myFixture.configureByText("a.jxml", "<Column controller=\"com.acme.Ctl\"/>");
        myFixture.renameElement(ctl(), "Screen");
        assertEquals("<Column controller=\"com.acme.Screen\"/>", myFixture.getEditor().getDocument().getText());
    }
}
