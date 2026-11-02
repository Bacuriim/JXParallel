package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jxparallel.ui.JXComponent;
import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXProps;

/** Controller of feature.jxml: records which handlers ran. */
public class FeatureController {
    public final List<String> calls = new ArrayList<String>();
    private final Customer customer = new Customer();

    public Customer getCustomer() {
        return customer;
    }

    public String getStatus() {
        return "Ready";
    }

    public double getProgress() {
        return 0.25;
    }

    public boolean getBusy() {
        return false;
    }

    public int getTab() {
        return 1;
    }

    public List<String> getCities() {
        return Arrays.asList("Fortaleza", "Recife");
    }

    /** A widget written in Java, hosted by a Slot. */
    public JXComponent getBanner() {
        return new JXComponent() {
            @Override
            public JXElement render() {
                return JXElement.of("#text", JXProps.builder().set("value", "hand-written").build());
            }
        };
    }

    public void save() {
        calls.add("save");
    }

    public void cancel() {
        calls.add("cancel");
    }

    /** A bean reached through a binding path. */
    public static class Customer {
        public String getName() {
            return "Ada";
        }

        /** Accessor in record style ({@code ${customer.city()}}). */
        public String city() {
            return "Fortaleza";
        }
    }
}
