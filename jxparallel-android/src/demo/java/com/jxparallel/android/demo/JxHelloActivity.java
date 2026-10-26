package com.jxparallel.android.demo;

import android.app.Activity;
import android.os.Bundle;

import com.jxparallel.android.JXAndroidView;
import com.jxparallel.android.demo.gen.HelloView;

/** Shows hello.jxml (compiled to {@link HelloView} at build time) in a {@link JXAndroidView}. */
public class JxHelloActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final HelloController controller = new HelloController();
        final HelloView screen = new HelloView();
        JXAndroidView view = new JXAndroidView(this);
        view.setContent(() -> screen.build(controller));
        setContentView(view);
    }
}
