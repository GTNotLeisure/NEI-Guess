package com.asdflj.nech.utils.matching;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import me.towdium.pinin.PinIn;

public class PhoneticContexts {

    private final Supplier<PinIn> factory;
    private final ThreadLocal<LocalContext> contexts = new ThreadLocal<>();
    private volatile Configuration configuration = new Configuration(config -> {});

    public PhoneticContexts(Supplier<PinIn> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public void configure(Consumer<PinIn.Config> update) {
        configuration = new Configuration(Objects.requireNonNull(update, "update"));
    }

    public PinIn current() {
        Configuration expected = configuration;
        LocalContext local = contexts.get();
        if (local == null) {
            PinIn context = factory.get();
            expected.apply(context);
            local = new LocalContext(context, expected);
            contexts.set(local);
        } else if (local.configuration != expected) {
            expected.apply(local.context);
            local.configuration = expected;
        }
        return local.context;
    }

    private record Configuration(Consumer<PinIn.Config> update) {

        private void apply(PinIn context) {
            PinIn.Config config = context.config();
            update.accept(config);
            config.commit();
        }
    }

    private static class LocalContext {

        private final PinIn context;
        private Configuration configuration;

        private LocalContext(PinIn context, Configuration configuration) {
            this.context = context;
            this.configuration = configuration;
        }
    }
}
