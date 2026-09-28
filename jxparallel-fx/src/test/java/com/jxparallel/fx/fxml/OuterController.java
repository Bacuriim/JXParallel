package com.jxparallel.fx.fxml;

import com.jxparallel.fx.scene.control.Label;
import com.jxparallel.fx.scene.control.Spinner;
import com.jxparallel.fx.scene.layout.HBox;
import javafx.fxml.FXML;

public class OuterController {
    @FXML Label title;
    @FXML Spinner<Integer> spinner;
    @FXML HBox inner;
    @FXML InnerController innerController;
}
