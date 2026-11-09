package com.jxparallel.ui.native2d;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.jxparallel.forge.JxmlTemplate;

/**
 * A window with the controls of {@link JxmlComponentBehaviorTest}, to try them by hand: the wheel and the bars of a list
 * of 200 items, tabs, a slider, a spinner, text fields with selection and the clipboard, buttons with a tooltip.
 * Run with the test classpath: {@code java -cp ... com.jxparallel.ui.native2d.BehaviorDemo}.
 */
public final class BehaviorDemo {
    private static final String SCREEN = "<Border controller=\"" + Demo.class.getName() + "\">\n"
            + "<Column slot=\"top\" padding=\"10\" gap=\"8\">\n"
            + "  <Row gap=\"8\"><TextField text=\"@{name}\" prompt=\"Name\" hgrow=\"always\"/>"
            + "<Button text=\"Save\" defaultButton=\"true\" tooltip=\"Saves the name (Enter)\" onAction=\"#click\"/></Row>\n"
            + "  <TextArea text=\"@{notes}\" rows=\"3\" prefHeight=\"70\"/>\n"
            + "  <Row gap=\"8\"><Slider value=\"@{volume}\" hgrow=\"always\"/>"
            + "<Spinner value=\"${countText}\" min=\"0\" max=\"10\" onChange=\"#setCount\"/></Row>\n"
            + "  <Label text=\"${status}\"/>\n"
            + "</Column>\n"
            + "<Tabs slot=\"center\" selected=\"@{tab}\">\n"
            + "  <Tab title=\"List\"><ListView items=\"${rows}\" var=\"r\" type=\"java.lang.String\" first=\"@{top}\" rows=\"40\">"
            + "<ListItem text=\"${r}\" onAction=\"#pick(r)\"/></ListView></Tab>\n"
            + "  <Tab title=\"Pages\"><Pagination pageCount=\"8\" current=\"@{page}\"><Label text=\"${pageText}\"/></Pagination></Tab>\n"
            + "</Tabs>\n"
            + "</Border>";

    private BehaviorDemo() {
    }

    /** The controller: every change renders the screen again, as an application does. */
    public static final class Demo extends BehaviorController {
        Runnable render = () -> { };

        public String getStatus() {
            return "clicks " + clicks + " | volume " + Math.round(getVolume()) + " | count " + getCountText() + " | top row "
                    + getTop() + (picked.isEmpty() ? "" : " | picked " + picked.get(picked.size() - 1));
        }

        public String getPageText() {
            return "Page " + (getPage() + 1);
        }

        @Override public void click() { super.click(); render.run(); }
        @Override public void pick(String row) { super.pick(row); render.run(); }
        @Override public void setName(String v) { super.setName(v); render.run(); }
        @Override public void setNotes(String v) { super.setNotes(v); render.run(); }
        @Override public void setVolume(double v) { super.setVolume(v); render.run(); }
        @Override public void setCount(double v) { super.setCount(v); render.run(); }
        @Override public void setTab(int v) { super.setTab(v); render.run(); }
        @Override public void setTop(int v) { super.setTop(v); render.run(); }
        @Override public void setPage(int v) { super.setPage(v); render.run(); }
    }

    public static void main(String[] args) {
        JxmlTemplate template = JxmlTemplate.read(new ByteArrayInputStream(SCREEN.getBytes(StandardCharsets.UTF_8)), "demo.jxml");
        Demo demo = new Demo();
        JXWindow window = new JXWindow("JXParallel behaviour demo");
        demo.render = () -> window.invokeLater(() -> window.setContent(template.build(demo)));
        window.setSize(520, 640);
        window.setContent(template.build(demo));
        window.show();
    }
}
