package io.github.bacuriim.jxforge;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/** A project with a controller, a record, a bean and a generic list, for tests of the JXML references. */
public abstract class JxmlJavaTestCase extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return JAVA_17;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package com.acme; public record Order(String name, int total) { public String label() { return name; } }");
        myFixture.addClass("package com.acme; public class Customer {\n"
                + " private String name;\n"
                + " public String getName() { return name; }\n"
                + " public void setName(String n) { name = n; }\n"
                + " public String getEmail() { return \"\"; }\n"
                + " public Address getAddress() { return null; }\n}");
        myFixture.addClass("package com.acme; public class Address { public String getCity() { return \"\"; } }");
        myFixture.addClass("package com.acme; import java.util.List;\n"
                + "public class Ctl {\n"
                + " public String getTitle() { return \"\"; }\n"
                + " public void setTitle(String t) { }\n"
                + " public String getReadOnly() { return \"\"; }\n"
                + " public boolean isAdmin() { return true; }\n"
                + " public Customer getCustomer() { return null; }\n"
                + " public List<Order> getOrders() { return null; }\n"
                + " public Order[] getOrderArray() { return null; }\n"
                + " public Order getCurrent() { return null; }\n"
                + " public void save() { }\n"
                + " public void open(Order o) { }\n"
                + " public void rename(Order o, String name) { }\n"
                + " private void hidden() { }\n"
                + "}");
    }

    protected PsiClass ctl() {
        return myFixture.findClass("com.acme.Ctl");
    }

    protected PsiMethod method(String className, String name) {
        PsiMethod[] methods = myFixture.findClass(className).findMethodsByName(name, false);
        assertEquals(className + "." + name, 1, methods.length);
        return methods[0];
    }

    /** A screen of the controller {@code com.acme.Ctl} whose root holds {@code body}. */
    protected static String screen(String body) {
        return "<Column controller=\"com.acme.Ctl\">\n" + body + "\n</Column>";
    }

    protected PsiElement resolveAtCaret() {
        PsiReference ref = myFixture.getReferenceAtCaretPositionWithAssertion();
        return ref.resolve();
    }

    protected List<HighlightInfo> highlights(HighlightSeverity min) {
        return myFixture.doHighlighting().stream().filter(i -> i.getSeverity().compareTo(min) >= 0).collect(Collectors.toList());
    }

    protected String describe(List<HighlightInfo> infos) {
        return infos.stream().map(i -> i.getSeverity() + " '" + i.getText() + "' " + i.getDescription()).collect(Collectors.joining("\n"));
    }
}
