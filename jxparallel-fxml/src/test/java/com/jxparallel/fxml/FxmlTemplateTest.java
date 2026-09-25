package com.jxparallel.fxml;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.jxparallel.fxml.template.TemplateBeans.Box;
import com.jxparallel.fxml.template.TemplateBeans.Controller;
import com.jxparallel.fxml.template.TemplateBeans.Grid;
import com.jxparallel.fxml.template.TemplateBeans.Item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlTemplateTest {
    private static final String FXML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<?import com.jxparallel.fxml.template.TemplateBeans.*?>\n"
            + "<?import com.jxparallel.fxml.template.TemplateBeans.Grid?>\n"
            + "<Box xmlns:fx=\"http://javafx.com/fxml\" fx:controller=\"com.jxparallel.fxml.template.TemplateBeans$Controller\"\n"
            + "     title=\"Root\" size=\"3\" color=\"#ff0000\">\n"
            + "  <Box fx:id=\"inner\" title=\"Inner\" onAction=\"#onAction\">\n"
            + "    <Item fx:id=\"first\" a=\"1\" b=\"2\" Grid.rank=\"7\"/>\n"
            + "    <Item fx:id=\"notAnnotated\" a=\"1.5\" b=\"2\"/>\n"
            + "  </Box>\n"
            + "  <footer><Item a=\"9\" b=\"9\"/></footer>\n"
            + "</Box>\n";

    private static FxmlTemplate template(String fxml) throws FxmlTemplate.Unsupported {
        return FxmlTemplate.parse(fxml.getBytes(StandardCharsets.UTF_8), null, FxmlTemplateTest.class.getClassLoader());
    }

    @Test
    void buildsTheObjectGraphAndWiresTheController() throws Exception {
        Box root = template(FXML).instantiate();

        assertEquals("Root", root.getTitle());
        assertEquals(3, root.getSize());
        assertEquals("#ff0000", root.getColor(), "# outside on* attributes is a value, not a handler");
        Box inner = (Box) root.getChildren().get(0);
        Item first = (Item) inner.getChildren().get(0);
        Item second = (Item) inner.getChildren().get(1);
        assertEquals(Integer.valueOf(1), first.getA(), "int constructor preferred for integral values");
        assertEquals(Double.valueOf(1.5), second.getA(), "double constructor when the value needs it");
        assertEquals(Integer.valueOf(7), Grid.getRank(first));
        assertTrue(root.getFooter() instanceof Item);
        assertEquals("inner", inner.getId(), "fx:id also sets the id property, like FXMLLoader");

        Controller controller = Controller.CREATED.get(Controller.CREATED.size() - 1);
        assertTrue(controller.isInitialized(), "fields injected before initialize()");
        assertNull(controller.getNotAnnotated(), "only @FXML or public fields are injected");
        inner.fire();
        assertEquals(1, controller.getActions());
    }

    @Test
    void everyInstanceIsIndependent() throws Exception {
        FxmlTemplate template = template(FXML);
        Box a = template.instantiate();
        Box b = template.instantiate();

        assertNotSame(a, b);
        assertNotSame(a.getChildren().get(0), b.getChildren().get(0));
        Controller second = Controller.CREATED.get(Controller.CREATED.size() - 1);
        Controller first = Controller.CREATED.get(Controller.CREATED.size() - 2);
        assertNotSame(first, second);
        assertNotSame(first.getInner(), second.getInner());
    }

    @Test
    void unsupportedFeaturesAskForFxmlLoader() {
        String include = FXML.replace("<footer>", "<fx:include source=\"other.fxml\"/><footer>");
        String expression = FXML.replace("title=\"Inner\"", "title=\"${inner.title}\"");
        String resource = FXML.replace("title=\"Inner\"", "title=\"%inner.title\"");

        assertThrows(FxmlTemplate.Unsupported.class, () -> template(include));
        assertThrows(FxmlTemplate.Unsupported.class, () -> template(expression));
        assertThrows(FxmlTemplate.Unsupported.class, () -> template(resource));
    }
}
