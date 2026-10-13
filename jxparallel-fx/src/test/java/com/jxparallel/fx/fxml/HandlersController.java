package com.jxparallel.fx.fxml;

import com.jxparallel.fx.scene.control.Button;

/** A handler with an overload that takes something other than an event, such as setItems(Record). */
public class HandlersController {
    @FXML
    public Button button;
    public String called;

    public void open() {
        called = "open()";
    }

    public void open(String record) {
        called = "open(String)";
    }
}
