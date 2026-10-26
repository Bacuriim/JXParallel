package com.jxparallel.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

import com.jxparallel.ui.JXElement;
import com.jxparallel.ui.JXLiveNode;
import com.jxparallel.ui.JXLiveSession;

/**
 * Research: a screen kept live. {@link JxmlView#live} builds the element tree of a screen exactly as {@code build} does
 * and records, from the plan the compiler generated beside the build code, where the screen depends on the controller:
 * for every element with a bound prop, the getter that reads the value (the same conversion {@code build} uses) and the
 * value it last had; for every {@code <For>}, {@code <If>} and {@code <Slot>}, what decides the shape of the tree. After the host
 * mounted the tree ({@link #attach}), {@link #refresh} is what the host calls when the controller changed:
 *
 * <ol>
 * <li><b>Guards</b>, all of them first: the size and window of every {@code <For>} (and, for a keyed element, its key, by
 * identity as a keyed reconcile compares them), the test of every {@code <If>}, the element of every {@code <Slot>}, and
 * a loop whose items a handler captured. Any that changed means the shape of the tree may differ, and
 * the refresh <em>falls back</em>: the screen is built again and reconciled into the mounted tree, as before, and the
 * recording is made again. Nothing has been written to the mounted tree when a guard fails.</li>
 * <li><b>Bindings</b>: each getter is read again and compared with the last value ({@code Objects.deepEquals}); only a changed
 * one is written, with {@link JXLiveNode#setProp}, into the node (and so into the element, which is the same props object).
 * The parts of the screen the plan marks <em>opaque</em> ({@code Tabs}, trees, the virtual lists, a radio button that is in a
 * group) are built again and reconciled into their own node, as before, and nothing else of the screen is.</li>
 * <li><b>Settling</b>: the nodes that changed, and only the chain of their ancestors that has a recorded element, are
 * settled bottom up ({@link JXLiveNode#commit}), once each, with the results a reconcile would have given.</li>
 * </ol>
 *
 * <p>A loop keeps one {@link Scope} for each of its iterations, which holds the item; a refresh moves the new item of each
 * position into the scope, so the loop's list may be another object of the same size (the usual case: a model that is
 * replaced by a new snapshot). A handler that captured a loop variable (rather than the controller) would still hold the
 * previous item, so such a loop falls back when an item changed.
 *
 * <p>The session owns the props of the elements it recorded: it writes into them. Never keep the element tree of a
 * live session to compare with another, and never hand the same element tree to two sessions.
 */
public final class JxmlLive<C> implements JXLiveSession {
    // ---- what the generated code uses: the plan ------------------------------------------------------------------

    /** The item of one iteration of a loop, and the scope of the loop around it; the generated getters read both. */
    public static final class Scope {
        public final Scope parent;
        public Object item;

        Scope(Scope parent, Object item) {
            this.parent = parent;
            this.item = item;
        }
    }

    /** Reads one value from the controller and the loop variables in scope, exactly as the build does. */
    public interface Getter<C> {
        Object get(C controller, Scope scope);
    }

    /** What the screen is made of, in the order of the elements it builds. */
    public abstract static class Plan {
        Plan() {
        }
    }

    private static final class Skip extends Plan {
    }

    private static final Plan SKIP = new Skip();

    /** An element that is the same for every controller and locale (or whose only dynamic parts are not bindings). */
    public static Plan skip() {
        return SKIP;
    }

    /** A prop that depends on the controller. {@code guard}: its change means another shape (compared by identity). */
    public static final class Bind {
        final String name;
        final Getter<Object> getter;
        final boolean guard;
        /** A translated text: it changes with the locale and with the bound arguments of the message, nothing else. */
        final boolean translated;
        /** The raw arguments the message reads; the text is made again only when one of them (or the locale) changed. Null: always. */
        final Getter<Object>[] deps;

        Bind(String name, Getter<?> getter, boolean guard, boolean translated, Getter<?>[] deps) {
            this.name = name;
            this.getter = cast(getter);
            this.guard = guard;
            this.translated = translated;
            this.deps = deps == null ? null : castAll(deps);
        }
    }

    public static Bind bind(String name, Getter<?> getter) {
        return new Bind(name, getter, false, false, null);
    }

    public static Bind guard(String name, Getter<?> getter) {
        return new Bind(name, getter, true, false, null);
    }

    /**
     * A prop that is a translation ({@code %{key(args)}}): re-read when the locale changed or when one of {@code deps}, the
     * bound arguments of its messages, changed (formatting a message makes garbage, so an unchanged one is not formatted
     * again). {@code deps} null: always re-read (an argument that may change in place, such as a date).
     */
    public static Bind translated(String name, Getter<?> getter, Getter<?>[] deps) {
        return new Bind(name, getter, false, true, deps);
    }

    @SuppressWarnings("unchecked")
    private static Getter<Object>[] castAll(Getter<?>[] getters) {
        return (Getter<Object>[]) getters;
    }

    static final class Elem extends Plan {
        final Bind[] binds;
        /** null: nothing below changes, whatever its number of children. */
        final Plan[] children;

        Elem(Bind[] binds, Plan[] children) {
            this.binds = binds;
            this.children = children;
        }
    }

    /** A plain element with bindings and/or dynamic children. */
    public static Plan elem(Bind[] binds, Plan[] children) {
        return new Elem(binds, children);
    }

    static final class Each extends Plan {
        final Getter<Object> items;
        final Getter<Object> from;
        final Getter<Object> limit;
        final boolean memo;
        final boolean capturing;
        final Plan[] body;

        Each(Getter<?> items, Getter<?> from, Getter<?> limit, boolean memo, boolean capturing, Plan[] body) {
            this.items = cast(items);
            this.from = cast(from);
            this.limit = cast(limit);
            this.memo = memo;
            this.capturing = capturing;
            this.body = body;
        }
    }

    /** A {@code <For>}: {@code capturing} when a handler in the body holds the loop variable; {@code memo} for {@code memo="true"}. */
    public static Plan each(Getter<?> items, Getter<?> from, Getter<?> limit, boolean memo, boolean capturing, Plan[] body) {
        return new Each(items, from, limit, memo, capturing, body);
    }

    static final class When extends Plan {
        final Getter<Object> test;
        final Plan[] then;
        final Plan[] otherwise;

        When(Getter<?> test, Plan[] then, Plan[] otherwise) {
            this.test = cast(test);
            this.then = then;
            this.otherwise = otherwise;
        }
    }

    public static Plan when(Getter<?> test, Plan[] then, Plan[] otherwise) {
        return new When(test, then, otherwise);
    }

    static final class Hole extends Plan {
        final Getter<Object> value;

        Hole(Getter<?> value) {
            this.value = cast(value);
        }
    }

    /** A {@code <Slot>}: an element the controller supplies, as is. */
    public static Plan hole(Getter<?> value) {
        return new Hole(value);
    }

    static final class Opaque extends Plan {
        final Getter<Object> build;
        final Getter<Object> key;

        Opaque(Getter<?> build, Getter<?> key) {
            this.build = cast(build);
            this.key = cast(key);
        }
    }

    /** An element that is built again and reconciled into its node on every refresh; {@code key} guards its key, or null. */
    public static Plan opaque(Getter<?> build, Getter<?> key) {
        return new Opaque(build, key);
    }

    @SuppressWarnings("unchecked")
    private static Getter<Object> cast(Getter<?> getter) {
        return (Getter<Object>) getter;
    }

    // ---- the recording ------------------------------------------------------------------------------------------

    /** In {@link Site#bits}: the site is on the list of those to settle. */
    private static final int DIRTY = 8;
    /** The low bits of {@link Site#bits}: what {@code commit} is told, and {@link #DIRTY}; above them, the child index. */
    private static final int FLAGS = 0xFF;
    private static final int INDEX_SHIFT = 8;

    /** An element with something live below or on it: its node, and what the refresh found out about it. */
    private static class Site {
        /** The element as it is now: the very one the node has as its source (an ancestor of a rebuilt part is replaced). */
        JXElement element;
        final Site parent;
        final int order;
        JXLiveNode node;
        /**
         * What {@link JXLiveNode#commit} will be told ({@link JXLiveNode#RELAYOUT} and the like) and {@link #DIRTY} in the
         * low byte, and above it the position of the element among the children of its parent's element.
         */
        int bits;

        Site(JXElement element, Site parent, int order, int index) {
            if (index >= 1 << (31 - INDEX_SHIFT)) {
                throw new Desync("an element with " + index + " siblings before it");
            }
            this.element = element;
            this.parent = parent;
            this.order = order;
            this.bits = index << INDEX_SHIFT;
        }

        int index() {
            return bits >>> INDEX_SHIFT;
        }
    }

    private static final class OpaqueSite extends Site {
        final Getter<Object> build;
        final Scope scope;

        OpaqueSite(JXElement element, Site parent, int order, int index, Getter<Object> build, Scope scope) {
            super(element, parent, order, index);
            this.build = build;
            this.scope = scope;
        }
    }

    private static final class Binding {
        final Site site;
        final Bind bind;
        final Scope scope;
        Object last;
        /** A translation's arguments as they were when it was last made; null for the others. */
        Object[] argsLast;

        Binding(Site site, Bind bind, Scope scope, Object last, Object[] argsLast) {
            this.site = site;
            this.bind = bind;
            this.scope = scope;
            this.last = last;
            this.argsLast = argsLast;
        }
    }

    private abstract static class Check {
        /** True when the shape of the tree is what it was. */
        abstract boolean ok(JxmlLive<?> live);
    }

    /** A key or an element-valued prop: any change is another shape. */
    private static final class GuardCheck extends Check {
        final Getter<Object> getter;
        final Scope scope;
        final Object last;

        GuardCheck(Getter<Object> getter, Scope scope, Object last) {
            this.getter = getter;
            this.scope = scope;
            this.last = last;
        }

        @Override
        boolean ok(JxmlLive<?> live) {
            return getter.get(live.controller, scope) == last;
        }
    }

    private static final class WhenCheck extends Check {
        final When plan;
        final Scope scope;
        final boolean last;

        WhenCheck(When plan, Scope scope, boolean last) {
            this.plan = plan;
            this.scope = scope;
            this.last = last;
        }

        @Override
        boolean ok(JxmlLive<?> live) {
            return ((Boolean) plan.test.get(live.controller, scope)).booleanValue() == last;
        }
    }

    private static final class HoleCheck extends Check {
        final Hole plan;
        final Scope scope;
        final Object last;

        HoleCheck(Hole plan, Scope scope, Object last) {
            this.plan = plan;
            this.scope = scope;
            this.last = last;
        }

        @Override
        boolean ok(JxmlLive<?> live) {
            return plan.value.get(live.controller, scope) == last;
        }
    }

    private static final class EachCheck extends Check {
        final Each plan;
        final Scope outer;
        final Object source;
        final Integer from;
        final Integer limit;
        /** For {@code memo}: the size; else the scopes of the iterations, whose items a refresh replaces. */
        final int size;
        final Scope[] scopes;

        EachCheck(Each plan, Scope outer, Object source, Integer from, Integer limit, int size, Scope[] scopes) {
            this.plan = plan;
            this.outer = outer;
            this.source = source;
            this.from = from;
            this.limit = limit;
            this.size = size;
            this.scopes = scopes;
        }

        @Override
        boolean ok(JxmlLive<?> live) {
            Object c = live.controller;
            Object now = plan.items.get(c, outer);
            Integer f = plan.from == null ? null : (Integer) plan.from.get(c, outer);
            Integer l = plan.limit == null ? null : (Integer) plan.limit.get(c, outer);
            if (!Objects.equals(f, from) || !Objects.equals(l, limit)) {
                return false;
            }
            if (plan.memo) { // the memoized build gives the same elements for the same list of the same size: nothing else is read
                return now == source && sizeOf(now) == size;
            }
            Iterable<Object> items = JxmlRuntime.items(now, f, l);
            int i = 0;
            if (items instanceof RandomAccess) {
                List<Object> list = (List<Object>) items;
                if (list.size() != scopes.length) {
                    return false;
                }
                for (; i < scopes.length; i++) {
                    if (!rebind(scopes[i], list.get(i))) {
                        return false;
                    }
                }
                return true;
            }
            for (Object item : items) {
                if (i >= scopes.length || !rebind(scopes[i], item)) {
                    return false;
                }
                i++;
            }
            return i == scopes.length;
        }

        private boolean rebind(Scope scope, Object item) {
            if (scope.item != item) {
                if (plan.capturing) {
                    return false; // a handler built for the previous item would still hold it
                }
                scope.item = item;
            }
            return true;
        }
    }

    static int sizeOf(Object items) {
        if (items == null) {
            return 0;
        }
        if (items instanceof java.util.Collection) {
            return ((java.util.Collection<?>) items).size();
        }
        if (items.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(items);
        }
        return -1;
    }

    /** The recording of a screen is out of step with its tree: this session can only fall back. */
    private static final class Desync extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Desync(String message) {
            super(message, null, false, false);
        }
    }

    // ---- the session ----------------------------------------------------------------------------------------------

    private final JxmlView<C> view;
    private final Plan plan;
    final C controller;
    private JXElement element;
    private JXLiveNode root;
    private boolean recorded;
    private boolean attached;

    private final ArrayList<Site> siteList = new ArrayList<Site>();
    private final ArrayList<Check> checkList = new ArrayList<Check>();
    private final ArrayList<Binding> bindingList = new ArrayList<Binding>();
    private final ArrayList<OpaqueSite> opaqueList = new ArrayList<OpaqueSite>();
    private Site[] sites = new Site[0];
    private Check[] checks = new Check[0];
    private Binding[] bindings = new Binding[0];
    private OpaqueSite[] opaques = new OpaqueSite[0];
    private Site[] heap = new Site[16];
    private int heapSize;

    private int refreshes;
    private int fallbacks;
    private int changed;
    private int opaqueBuilds;
    private String desync;

    private JxmlLive(JxmlView<C> view, Plan plan, C controller) {
        this.view = view;
        this.plan = plan;
        this.controller = controller;
    }

    /**
     * What a generated view's {@code live} returns: the screen built for {@code controller} and recorded by {@code plan}
     * (null: nothing is recorded and every refresh falls back, which is what the interpreter does).
     */
    public static <C> JxmlLive<C> of(JxmlView<C> view, Plan plan, C controller) {
        return of(view, plan, controller, null);
    }

    /** As {@link #of(JxmlView, Plan, Object)}; {@code locale} reads the current locale of the screen's translations (null: it has none). */
    public static <C> JxmlLive<C> of(JxmlView<C> view, Plan plan, C controller, java.util.function.Supplier<java.util.Locale> locale) {
        JxmlLive<C> live = new JxmlLive<C>(view, plan, controller);
        live.locale = locale;
        live.element = view.build(controller);
        live.record();
        return live;
    }

    private java.util.function.Supplier<java.util.Locale> locale;
    private java.util.Locale lastLocale;

    /** A session for the same view and controller, built now: what a host mounts when {@link #refresh} says the root changed. */
    @Override
    public JxmlLive<C> renew() {
        return view.live(controller);
    }

    /** The element tree of the screen as of the last build; the props of the recorded elements are the live ones. */
    @Override
    public JXElement element() {
        return element;
    }

    /** True while the recording is paired with a mounted tree, so that a refresh does not have to build the screen. */
    public boolean isLive() {
        return attached;
    }

    /** Why the recording could not be made or paired, or null. */
    public String problem() {
        return desync;
    }

    public int refreshes() {
        return refreshes;
    }

    /** The refreshes that built the screen again and reconciled it. */
    public int fallbacks() {
        return fallbacks;
    }

    /** The props written in place, over all refreshes. */
    public int changes() {
        return changed;
    }

    /** The opaque parts built again, over all refreshes. */
    public int opaqueBuilds() {
        return opaqueBuilds;
    }

    /** Recorded elements, bindings, checks and opaque parts: what the recording holds. */
    public String describe() {
        return sites.length + " sites, " + bindings.length + " bindings, " + checks.length + " checks, " + opaques.length + " opaque";
    }

    private void record() {
        siteList.clear();
        checkList.clear();
        bindingList.clear();
        opaqueList.clear();
        recorded = false;
        attached = false;
        sites = new Site[0];
        checks = new Check[0];
        bindings = new Binding[0];
        opaques = new OpaqueSite[0];
        lastLocale = locale == null ? null : locale.get();
        dirtyCount = 0;
        highest = 0;
        heapSize = 0;
        heapMode = false;
        staged = new JXElement[0][];
        if (plan == null) {
            return;
        }
        if (plan == SKIP) { // nothing in the screen depends on the controller or the locale
            recorded = true;
            return;
        }
        try {
            int used = walk(new Plan[] {plan}, java.util.Collections.singletonList(element), 0, null, null, false);
            if (used != 1) {
                throw new Desync("the plan used " + used + " of 1 root element");
            }
            sites = siteList.toArray(new Site[0]);
            checks = checkList.toArray(new Check[0]);
            bindings = bindingList.toArray(new Binding[0]);
            opaques = opaqueList.toArray(new OpaqueSite[0]);
            recorded = true;
        } catch (Desync | ClassCastException | IndexOutOfBoundsException e) {
            desync = e.toString();
            sites = new Site[0];
            checks = new Check[0];
            bindings = new Binding[0];
            opaques = new OpaqueSite[0];
        }
        siteList.clear();
        checkList.clear();
        bindingList.clear();
        opaqueList.clear();
    }

    /**
     * Records {@code plans} against the elements from {@code at}; returns how many elements it used. {@code frozen}: inside a
     * {@code memo} loop, whose elements are made once and shared: they are counted, not recorded.
     */
    @SuppressWarnings("unchecked")
    private int walk(Plan[] plans, List<JXElement> elements, int at, Site parent, Scope scope, boolean frozen) {
        for (Plan p : plans) {
            if (p == SKIP) {
                at = take(elements, at);
            } else if (p instanceof Elem) {
                Elem e = (Elem) p;
                JXElement element = elements.get(at++);
                if (frozen) {
                    if (e.children != null) {
                        expect(walk(e.children, element.getChildren(), 0, null, scope, true), element);
                    }
                    continue;
                }
                Site site = new Site(element, parent, siteList.size(), at - 1);
                siteList.add(site);
                for (Bind b : e.binds) {
                    Object last = element.getProps().get(b.name);
                    if (b.guard) {
                        checkList.add(new GuardCheck(b.getter, scope, last));
                    } else {
                        Object[] args = null;
                        if (b.translated && b.deps != null) {
                            args = new Object[b.deps.length];
                            for (int i = 0; i < args.length; i++) {
                                args[i] = b.deps[i].get(controller, scope);
                            }
                        }
                        bindingList.add(new Binding(site, b, scope, last, args));
                    }
                }
                if (e.children != null) {
                    expect(walk(e.children, element.getChildren(), 0, site, scope, false), element);
                }
            } else if (p instanceof Each) {
                Each each = (Each) p;
                Object source = each.items.get(controller, scope);
                Integer from = each.from == null ? null : (Integer) each.from.get(controller, scope);
                Integer limit = each.limit == null ? null : (Integer) each.limit.get(controller, scope);
                int slot = checkList.size();
                if (!frozen) {
                    checkList.add(null); // before the checks of the body: a refresh moves the items into the scopes first
                }
                ArrayList<Scope> scopes = new ArrayList<Scope>();
                for (Object item : JxmlRuntime.<Object>items(source, from, limit)) {
                    Scope inner = new Scope(scope, item);
                    if (!frozen && !each.memo) {
                        scopes.add(inner);
                    }
                    at = walk(each.body, elements, at, parent, inner, frozen || each.memo);
                }
                if (!frozen) {
                    checkList.set(slot, new EachCheck(each, scope, source, from, limit, sizeOf(source),
                            each.memo ? null : scopes.toArray(new Scope[0])));
                }
            } else if (p instanceof When) {
                When when = (When) p;
                boolean test = ((Boolean) when.test.get(controller, scope)).booleanValue();
                if (!frozen) {
                    checkList.add(new WhenCheck(when, scope, test));
                }
                at = walk(test ? when.then : when.otherwise, elements, at, parent, scope, frozen);
            } else if (p instanceof Hole) {
                Hole hole = (Hole) p;
                Object value = hole.value.get(controller, scope);
                if (!frozen) {
                    checkList.add(new HoleCheck(hole, scope, value));
                }
                if (value != null) {
                    at = take(elements, at);
                }
            } else if (p instanceof Opaque) {
                Opaque o = (Opaque) p;
                JXElement element = elements.get(at++);
                if (!frozen) {
                    OpaqueSite site = new OpaqueSite(element, parent, siteList.size(), at - 1, o.build, scope);
                    siteList.add(site);
                    opaqueList.add(site);
                    if (o.key != null) {
                        checkList.add(new GuardCheck(o.key, scope, element.getProps().get("key")));
                    }
                }
            } else {
                throw new Desync("unknown plan " + p);
            }
        }
        return at;
    }

    private static int take(List<JXElement> elements, int at) {
        if (at >= elements.size()) {
            throw new Desync("the plan has more elements than the tree");
        }
        return at + 1;
    }

    private static void expect(int used, JXElement element) {
        if (used != element.getChildren().size()) {
            throw new Desync("the plan used " + used + " of the " + element.getChildren().size() + " children of <" + element.getType() + ">");
        }
    }

    // ---- pairing with the mounted tree ----------------------------------------------------------------------------

    private int next;

    /**
     * Pairs the recording with the nodes of {@code mounted}, which must be the tree {@link #element()} mounted (or reconciled
     * into) and not touched since. Returns false (and the session then only falls back) when it does not match.
     */
    @Override
    public boolean attach(JXLiveNode mounted) {
        this.root = mounted;
        attached = false;
        if (!recorded) {
            return false;
        }
        next = 0;
        for (Site s : sites) {
            s.node = null;
            s.bits &= ~FLAGS;
        }
        if (mounted.getSource() != element || !pair(mounted, element) || next != sites.length) {
            desync = "the mounted tree is not the tree of the recording";
            return false;
        }
        desync = null;
        attached = true;
        return true;
    }

    private boolean pair(JXLiveNode node, JXElement el) {
        if (next < sites.length && sites[next].element == el) {
            Site site = sites[next++];
            if (node.getSource() != el) {
                return false;
            }
            site.node = node;
            if (site instanceof OpaqueSite) {
                return true; // rebuilt as a whole: nothing below is recorded
            }
        }
        List<? extends JXLiveNode> nodes = node.getLiveChildren();
        List<JXElement> elements = el.getChildren();
        int n = elements.size();
        if (nodes.size() != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (!pair(nodes.get(i), elements.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ---- refreshing -----------------------------------------------------------------------------------------------

    /**
     * Brings the mounted tree up to date with the controller. Returns false when the screen's root is another type now: the
     * caller must mount {@code view.live(controller)} anew. The caller lays the tree out and paints afterwards, as after a reconcile.
     */
    @Override
    public boolean refresh() {
        refreshes++;
        if (root == null) {
            throw new IllegalStateException("attach the mounted tree first");
        }
        if (!attached) {
            return fallback();
        }
        for (Check check : checks) {
            if (!check.ok(this)) {
                return fallback();
            }
        }
        boolean again = false;
        C c = controller;
        java.util.Locale now = locale == null ? null : locale.get();
        boolean localeChanged = !Objects.equals(now, lastLocale);
        lastLocale = now;
        try {
            for (Binding b : bindings) {
                Bind bind = b.bind;
                if (bind.translated && bind.deps != null) { // a message is made again only for another locale or other arguments
                    boolean same = !localeChanged;
                    Getter<Object>[] deps = bind.deps;
                    for (int i = 0; i < deps.length; i++) {
                        Object arg = deps[i].get(c, b.scope);
                        if (!Objects.equals(arg, b.argsLast[i])) {
                            b.argsLast[i] = arg;
                            same = false;
                        }
                    }
                    if (same) {
                        continue;
                    }
                }
                Object value = bind.getter.get(c, b.scope);
                Object last = b.last;
                if (value == last || same(value, last)) {
                    continue;
                }
                int r = b.site.node.setProp(bind.name, value);
                if (r < 0) {
                    again = true; // a prop that appears or goes away is another set of keys: only a reconcile does that
                    continue;
                }
                b.last = value;
                changed++;
                Site site = b.site;
                site.bits |= ((r & JXLiveNode.SET_RELAYOUT) != 0 ? JXLiveNode.RELAYOUT : 0)
                        | ((r & JXLiveNode.SET_PLACEMENT) != 0 ? JXLiveNode.PLACEMENT : 0);
                markDirty(site);
            }
            for (OpaqueSite o : opaques) {
                JXElement built = (JXElement) o.build.get(c, o.scope);
                int r = o.node.reconcileLive(built);
                opaqueBuilds++;
                if (r < 0) {
                    again = true;
                    continue;
                }
                o.element = built;
                if (o.parent == null) {
                    element = built;
                } else {
                    stage(o.parent, o.index(), built);
                    if (r != JXLiveNode.UNCHANGED) {
                        o.parent.bits |= r == JXLiveNode.RESIZED ? JXLiveNode.RELAYOUT : JXLiveNode.CHILD_SUBTREE;
                        markDirty(o.parent);
                    }
                }
            }
            if (opaques.length > 0) {
                replaceAncestors();
            }
        } finally {
            settle();
        }
        return again ? fallback() : true;
    }

    /** The children each recorded element has now, where a rebuilt part took the place of one (by site, null where none did). */
    private JXElement[][] staged = new JXElement[0][];

    private void stage(Site parent, int index, JXElement child) {
        if (staged.length != sites.length) {
            staged = new JXElement[sites.length][];
        }
        JXElement[] children = staged[parent.order];
        if (children == null) {
            children = parent.element.getChildren().toArray(new JXElement[0]);
            staged[parent.order] = children;
            push(parent); // (the heap is idle until the sites are settled: it orders these by depth)
        }
        children[index] = child;
    }

    /**
     * An element is immutable, so a rebuilt part that took the place of another is a child the parent's element does not
     * have: the parent gets a new element (the same type and props, the new list of children), and so do its ancestors up to
     * the root, and each node is told. The element tree then is what is mounted, and the replaced parts can be collected.
     */
    private void replaceAncestors() {
        while (heapSize > 0) {
            Site site = pop();
            JXElement[] children = staged[site.order];
            staged[site.order] = null;
            JXElement now = JXElement.of(site.element.getType(), site.element.getProps(), children);
            site.element = now;
            site.node.setSource(now);
            if (site.parent == null) {
                element = now;
            } else {
                stage(site.parent, site.index(), now);
            }
        }
    }

    /** {@code Objects.deepEquals}, with the common case (two values of one class that is not an array) one call of equals. */
    private static boolean same(Object a, Object b) {
        if (a == null || b == null) {
            return false; // (the caller has seen that they are not both null)
        }
        Class<?> type = a.getClass();
        if (type == b.getClass() && !type.isArray()) {
            return a.equals(b);
        }
        return Objects.deepEquals(a, b);
    }

    private Site[] dirty = new Site[16];
    private int dirtyCount;
    private int highest;
    /** While settling few sites: a parent that becomes dirty also goes into the heap. */
    private boolean heapMode;

    private void markDirty(Site site) {
        if ((site.bits & DIRTY) != 0) {
            return;
        }
        site.bits |= DIRTY;
        if (dirtyCount == dirty.length) {
            dirty = java.util.Arrays.copyOf(dirty, dirtyCount * 2);
        }
        dirty[dirtyCount++] = site;
        if (site.order > highest) {
            highest = site.order;
        }
        if (heapMode) {
            push(site);
        }
    }

    /**
     * Settles the nodes that were touched, deepest first (a child's order is above its parent's), one commit each. When many
     * were touched the recorded sites are walked once from the highest dirty one down (a parent is always below its
     * children); when few, a heap keeps the cost in the number touched.
     */
    private void settle() {
        if (dirtyCount == 0) {
            return;
        }
        if (dirtyCount * 8 >= highest + 1) {
            for (int i = highest; i >= 0; i--) {
                Site site = sites[i];
                if ((site.bits & DIRTY) != 0) {
                    commit(site);
                }
            }
        } else {
            heapMode = true;
            for (int i = 0, n = dirtyCount; i < n; i++) {
                push(dirty[i]);
            }
            while (heapSize > 0) {
                commit(pop());
            }
            heapMode = false;
        }
        java.util.Arrays.fill(dirty, 0, dirtyCount, null);
        dirtyCount = 0;
        highest = 0;
    }

    private void commit(Site site) {
        int r = site.node.commit(site.bits & FLAGS & ~DIRTY);
        site.bits &= ~FLAGS;
        if (r != JXLiveNode.UNCHANGED && site.parent != null) {
            site.parent.bits |= r == JXLiveNode.RESIZED ? JXLiveNode.RELAYOUT : JXLiveNode.CHILD_SUBTREE;
            markDirty(site.parent);
        }
    }

    private void push(Site site) {
        if (heapSize == heap.length) {
            heap = java.util.Arrays.copyOf(heap, heapSize * 2);
        }
        int i = heapSize++;
        while (i > 0) {
            int up = (i - 1) >> 1;
            if (heap[up].order >= site.order) {
                break;
            }
            heap[i] = heap[up];
            i = up;
        }
        heap[i] = site;
    }

    private Site pop() {
        Site top = heap[0];
        Site last = heap[--heapSize];
        heap[heapSize] = null;
        if (heapSize > 0) {
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= heapSize) {
                    break;
                }
                if (child + 1 < heapSize && heap[child + 1].order > heap[child].order) {
                    child++;
                }
                if (heap[child].order <= last.order) {
                    break;
                }
                heap[i] = heap[child];
                i = child;
            }
            heap[i] = last;
        }
        return top;
    }

    /** The screen built again, reconciled into the mounted tree, and recorded again. */
    private boolean fallback() {
        fallbacks++;
        JXElement built = view.build(controller);
        if (root.reconcileLive(built) < 0) {
            return false;
        }
        element = built;
        record();
        if (recorded) {
            attach(root);
        }
        return true;
    }
}
