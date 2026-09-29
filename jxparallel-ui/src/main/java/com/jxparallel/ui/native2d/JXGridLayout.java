package com.jxparallel.ui.native2d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * JavaFX 21 GridPane layout: column and row constraints (min, pref, max, percent, grow,
 * alignment, fill), children spanning several cells, gaps, padding and margins, with GridPane's
 * pixel snapping and its own grow/shrink loop, and USE_PREF_SIZE (negative infinity) as a
 * constraint's min or max. Baseline alignment and content bias are not supported yet, and a child
 * that spans several tracks one of which is fixed (USE_PREF_SIZE) can end up a pixel off JavaFX in
 * a centred grid: JavaFX gives that span's extra size to the tracks in a way not reproduced here.
 *
 * <p>Props on the grid: {@code hgap}, {@code vgap}, {@code alignment}, {@code padding},
 * {@code columns} and {@code rows} (lists of maps with the ColumnConstraints / RowConstraints
 * property names). On children: {@code column}, {@code row}, {@code columnSpan}, {@code rowSpan},
 * {@code hgrow}, {@code vgrow}, {@code halignment}, {@code valignment}, {@code margin},
 * {@code cellFillWidth}, {@code cellFillHeight}.
 */
final class JXGridLayout {
    private static final double MAX = JXBoxLayout.MAX;
    /** JavaFX Region.USE_COMPUTED_SIZE: a constraint that is not set. */
    private static final double COMPUTED = -1;

    private JXGridLayout() {
    }

    static double[] sizes(JXNativeNode grid) {
        Axis columns = new Axis(grid, true);
        Axis rows = new Axis(grid, false);
        double[] padding = JXBoxLayout.rounded(grid.padding());
        double horizontal = padding[1] + padding[3];
        double vertical = padding[0] + padding[2];
        return new double[] {horizontal + columns.minSizes().totalWithMultiSize(),
                horizontal + columns.prefSizes().totalWithMultiSize(), MAX,
                vertical + rows.minSizes().totalWithMultiSize(),
                vertical + rows.prefSizes().totalWithMultiSize(), MAX};
    }

    static void layoutChildren(JXNativeNode grid) {
        Axis columns = new Axis(grid, true);
        Axis rows = new Axis(grid, false);
        double[] padding = JXBoxLayout.rounded(grid.padding());
        double top = padding[0];
        double right = padding[1];
        double bottom = padding[2];
        double left = padding[3];
        double width = grid.getWidth();
        double height = grid.getHeight();
        Composite heights = rows.prefSizes().copy();
        Composite widths = columns.prefSizes().copy();
        double rowTotal = rows.adjust(heights, height, top, bottom);
        double columnTotal = columns.adjust(widths, width, left, right);
        String alignment = grid.alignment();
        double x = left + offset(width - left - right, columnTotal, alignment.charAt(1));
        double y = top + offset(height - top - bottom, rowTotal, alignment.charAt(0));
        for (JXNativeNode child : grid.getChildren()) {
            int column = child.column();
            int row = child.row();
            double areaX = x;
            for (int j = 0; j < column; j++) {
                areaX += widths.sizes[j] + columns.gap;
            }
            double areaY = y;
            for (int j = 0; j < row; j++) {
                areaY += heights.sizes[j] + rows.gap;
            }
            double areaWidth = widths.sizes[column];
            for (int j = 2; j <= columns.span(child); j++) {
                areaWidth += widths.sizes[column + j - 1] + columns.gap;
            }
            double areaHeight = heights.sizes[row];
            for (int j = 2; j <= rows.span(child); j++) {
                areaHeight += heights.sizes[row + j - 1] + rows.gap;
            }
            char halign = child.halignment() != 0 ? child.halignment() : columns.alignment(column, 'L');
            char valign = child.valignment() != 0 ? child.valignment() : rows.alignment(row, 'C');
            boolean fillWidth = child.cellFillWidth() != null ? child.cellFillWidth() : columns.fill(column);
            boolean fillHeight = child.cellFillHeight() != null ? child.cellFillHeight() : rows.fill(row);
            JXBoxLayout.layoutInArea(grid, child, areaX, areaY, areaWidth, areaHeight, fillWidth, fillHeight,
                    halign, valign);
        }
    }

    private static double offset(double length, double content, char pos) {
        if (pos == 'C') {
            return (length - content) / 2;
        }
        if (pos == 'R' || pos == 'B') {
            return length - content;
        }
        return 0;
    }

    /** One direction of the grid: columns (horizontal) or rows. Mirrors GridPane's paired methods. */
    private static final class Axis {
        final boolean horizontal;
        final JXNativeNode grid;
        final List<Map<?, ?>> constraints;
        final int count;
        final double gap;
        final double[] percent;
        final double percentTotal;
        final int[] grow;
        private Composite minSizes;
        private Composite prefSizes;
        private Composite maxSizes;

        Axis(JXNativeNode grid, boolean horizontal) {
            this.grid = grid;
            this.horizontal = horizontal;
            this.constraints = constraints(grid.getProperty(horizontal ? "columns" : "rows"));
            this.gap = Math.round(horizontal ? grid.hgap() : grid.vgap());
            int n = constraints.size();
            for (JXNativeNode child : grid.getChildren()) {
                n = Math.max(n, start(child) + declaredSpan(child));
            }
            this.count = n;
            percent = new double[n];
            java.util.Arrays.fill(percent, -1);
            grow = new int[n];
            for (int i = 0; i < constraints.size(); i++) {
                double p = value(i, horizontal ? "percentWidth" : "percentHeight");
                if (p >= 0) {
                    percent[i] = p;
                }
                grow[i] = priority(constraints.get(i).get(horizontal ? "hgrow" : "vgrow"));
            }
            for (JXNativeNode child : grid.getChildren()) {
                // JavaFX reads the literal span here: REMAINING never counts as 1, even over one cell.
                if ((horizontal ? child.columnSpan() : child.rowSpan()) == 1) {
                    int index = start(child);
                    grow[index] = Math.max(grow[index], child.growPriority(horizontal));
                }
            }
            double total = 0;
            for (double p : percent) {
                if (p > 0) {
                    total += p;
                }
            }
            if (total > 100) {
                double weight = 100 / total;
                for (int i = 0; i < percent.length; i++) {
                    if (percent[i] > 0) {
                        percent[i] *= weight;
                    }
                }
                total = 100;
            }
            percentTotal = total;
        }

        private static List<Map<?, ?>> constraints(Object value) {
            if (!(value instanceof List)) {
                return Collections.emptyList();
            }
            List<Map<?, ?>> result = new ArrayList<Map<?, ?>>();
            for (Object item : (List<?>) value) {
                result.add(item instanceof Map ? (Map<?, ?>) item : Collections.emptyMap());
            }
            return result;
        }

        private static int priority(Object value) {
            if (!(value instanceof String)) {
                return 0;
            }
            return "always".equalsIgnoreCase((String) value) ? JXNativeNode.ALWAYS
                    : "sometimes".equalsIgnoreCase((String) value) ? JXNativeNode.SOMETIMES : 0;
        }

        /** A constraint value, or COMPUTED when it is not set. */
        double value(int index, String key) {
            if (index >= constraints.size()) {
                return COMPUTED;
            }
            Object value = constraints.get(index).get(key);
            if (value instanceof Number && ((Number) value).doubleValue() == Double.NEGATIVE_INFINITY && !key.startsWith("pref")) {
                // USE_PREF_SIZE: a min or max that follows the pref ("new ColumnConstraints(50)" is a fixed width)
                return value(index, horizontal ? "prefWidth" : "prefHeight");
            }
            return value instanceof Number && ((Number) value).doubleValue() >= 0 ? ((Number) value).doubleValue() : COMPUTED;
        }

        /** The max as set on the constraint, without resolving USE_PREF_SIZE; negative means unbounded. */
        double rawMax(int index) {
            if (index >= constraints.size()) {
                return COMPUTED;
            }
            Object value = constraints.get(index).get(horizontal ? "maxWidth" : "maxHeight");
            return value instanceof Number && ((Number) value).doubleValue() >= 0 ? ((Number) value).doubleValue() : COMPUTED;
        }

        char alignment(int index, char fallback) {
            if (index < constraints.size()) {
                Object value = constraints.get(index).get(horizontal ? "halignment" : "valignment");
                if (value instanceof String && !((String) value).isEmpty()) {
                    return Character.toUpperCase(((String) value).charAt(0));
                }
            }
            return fallback;
        }

        boolean fill(int index) {
            if (index < constraints.size()) {
                return !Boolean.FALSE.equals(constraints.get(index).get(horizontal ? "fillWidth" : "fillHeight"));
            }
            return true;
        }

        int start(JXNativeNode child) {
            return horizontal ? child.column() : child.row();
        }

        /** The span as set; GridPane.REMAINING (Integer.MAX_VALUE) counts as 1 when sizing the grid. */
        int declaredSpan(JXNativeNode child) {
            int span = horizontal ? child.columnSpan() : child.rowSpan();
            return span == Integer.MAX_VALUE ? 1 : span;
        }

        /** The span in cells: GridPane.REMAINING reaches the last column (row), like JavaFX. */
        int span(JXNativeNode child) {
            int span = horizontal ? child.columnSpan() : child.rowSpan();
            return span == Integer.MAX_VALUE ? Math.max(1, count - start(child)) : span;
        }

        double minKey(int i) {
            return value(i, horizontal ? "minWidth" : "minHeight");
        }

        double prefKey(int i) {
            return value(i, horizontal ? "prefWidth" : "prefHeight");
        }

        double maxKey(int i) {
            return value(i, horizontal ? "maxWidth" : "maxHeight");
        }

        double prefArea(JXNativeNode child) {
            return horizontal ? JXBoxLayout.prefAreaWidth(child) : JXBoxLayout.prefAreaHeight(child);
        }

        double minArea(JXNativeNode child) {
            return horizontal ? JXBoxLayout.minAreaWidth(child) : JXBoxLayout.minAreaHeight(child);
        }

        Composite newComposite(double initial) {
            return new Composite(count, percent, percentTotal, gap, initial);
        }

        /** GridPane.computePrefWidths / computePrefHeights without bias. */
        Composite prefSizes() {
            if (prefSizes != null) {
                return prefSizes;
            }
            Composite result = newComposite(0);
            for (int i = 0; i < constraints.size(); i++) {
                double min = minKey(i);
                double pref = prefKey(i);
                if (pref != COMPUTED) {
                    double snapped = Math.ceil(pref);
                    double max = maxKey(i);
                    if (min >= 0 || max >= 0) {
                        double lower = min < 0 ? 0 : Math.ceil(min);
                        double upper = max < 0 ? Double.POSITIVE_INFINITY : Math.ceil(max);
                        result.setPreset(i, JXBoxLayout.bounded(lower, snapped, upper));
                    } else {
                        result.setPreset(i, snapped);
                    }
                } else if (min > 0) {
                    result.sizes[i] = Math.ceil(min);
                }
            }
            for (JXNativeNode child : grid.getChildren()) {
                int start = start(child);
                int end = start + span(child) - 1;
                double area = prefArea(child);
                if (start == end && !result.isPreset(start)) {
                    double min = minKey(start);
                    double max = maxKey(start);
                    result.setMax(start, JXBoxLayout.bounded(min < 0 ? 0 : min, area, max < 0 ? MAX : max));
                } else if (start != end) {
                    result.setMaxMulti(start, end + 1, area);
                }
            }
            prefSizes = result;
            return result;
        }

        /** GridPane.computeMinWidths / computeMinHeights without bias. */
        Composite minSizes() {
            if (minSizes != null) {
                return minSizes;
            }
            Composite result = newComposite(0);
            for (int i = 0; i < constraints.size(); i++) {
                double min = minKey(i);
                if (min != COMPUTED) {
                    result.setPreset(i, Math.ceil(min));
                }
            }
            for (JXNativeNode child : grid.getChildren()) {
                int start = start(child);
                int end = start + span(child) - 1;
                double area = minArea(child);
                if (start == end && !result.isPreset(start)) {
                    result.setMax(start, area);
                } else if (start != end) {
                    result.setMaxMulti(start, end + 1, area);
                }
            }
            minSizes = result;
            return result;
        }

        /** GridPane.computeMaxWidths / computeMaxHeights. */
        Composite maxSizes() {
            if (maxSizes != null) {
                return maxSizes;
            }
            Composite result = newComposite(MAX);
            for (int i = 0; i < constraints.size(); i++) {
                double max = maxKey(i);
                if (max != COMPUTED) {
                    double snapped = Math.ceil(max);
                    double min = minKey(i);
                    result.setPreset(i, min >= 0 ? JXBoxLayout.bounded(Math.ceil(min), snapped, snapped) : snapped);
                }
            }
            maxSizes = result;
            return result;
        }

        /** GridPane.adjustColumnWidths / adjustRowHeights. Returns the total including gaps. */
        double adjust(Composite sizes, double length, double startInset, double endInset) {
            double gaps = gap * (count - 1);
            double content = length - startInset - endInset;
            if (percentTotal > 0) {
                double remainder = 0;
                for (int i = 0; i < percent.length; i++) {
                    if (percent[i] >= 0) {
                        double size = (content - gaps) * (percent[i] / 100);
                        double floor = Math.floor(size);
                        remainder += size - floor;
                        size = floor;
                        if (remainder >= 0.5) {
                            size++;
                            remainder = -1.0 + remainder;
                        }
                        sizes.sizes[i] = size;
                    }
                }
            }
            double total = sizes.total();
            if (percentTotal < 100) {
                double available = length - startInset - endInset - total;
                if (available != 0) {
                    double remaining = growToMultiSpanPreferred(sizes, available);
                    remaining = growOrShrink(sizes, JXNativeNode.ALWAYS, remaining);
                    remaining = growOrShrink(sizes, JXNativeNode.SOMETIMES, remaining);
                    total += available - remaining;
                }
            }
            return total;
        }

        /** GridPane.growToMultiSpanPreferredWidths / Heights. */
        private double growToMultiSpanPreferred(Composite sizes, double extra) {
            if (extra <= 0) {
                return extra;
            }
            TreeSet<Integer> always = new TreeSet<Integer>();
            TreeSet<Integer> sometimes = new TreeSet<Integer>();
            TreeSet<Integer> last = new TreeSet<Integer>();
            for (Map.Entry<Interval, Double> multi : sizes.multiSizes()) {
                Interval interval = multi.getKey();
                for (int i = interval.begin; i < interval.end; i++) {
                    if (percent[i] < 0) {
                        if (grow[i] == JXNativeNode.ALWAYS) {
                            always.add(i);
                        } else if (grow[i] == JXNativeNode.SOMETIMES) {
                            sometimes.add(i);
                        }
                    }
                }
                if (percent[interval.end - 1] < 0) {
                    last.add(interval.end - 1);
                }
            }
            double remaining = extra;
            remaining = spread(sizes, always, remaining, false);
            remaining = spread(sizes, sometimes, remaining, false);
            remaining = spread(sizes, last, remaining, true);
            return remaining;
        }

        private double spread(Composite sizes, TreeSet<Integer> targets, double remaining, boolean lastOfInterval) {
            while (targets.size() > 0 && remaining > targets.size()) {
                double portion = Math.floor(remaining / targets.size());
                for (Iterator<Integer> it = targets.iterator(); it.hasNext();) {
                    int i = it.next();
                    // the raw constraint: GridPane reads getMaxWidth() here, so USE_PREF_SIZE (negative) is unbounded
                    double maxOf = rawMax(i);
                    double actual = portion;
                    for (Map.Entry<Interval, Double> multi : sizes.multiSizes()) {
                        Interval interval = multi.getKey();
                        if (lastOfInterval ? interval.end - 1 == i : interval.contains(i)) {
                            double current = sizes.total(interval.begin, interval.end);
                            if (lastOfInterval) {
                                actual = Math.min(Math.max(0, multi.getValue() - current), actual);
                            } else {
                                int members = 0;
                                for (int j = interval.begin; j < interval.end; j++) {
                                    if (targets.contains(j)) {
                                        members++;
                                    }
                                }
                                actual = Math.min(Math.floor(Math.max(0, (multi.getValue() - current) / members)), actual);
                            }
                        }
                    }
                    double current = sizes.sizes[i];
                    double bounded = maxOf >= 0 ? JXBoxLayout.bounded(0, current + actual, maxOf) : current + actual;
                    double used = bounded - current;
                    remaining -= used;
                    if (used != actual || used == 0) {
                        it.remove();
                    }
                    sizes.sizes[i] = bounded;
                }
            }
            return remaining;
        }

        /** GridPane.growOrShrinkColumnWidths / RowHeights, including its remainder handling. */
        private double growOrShrink(Composite sizes, int priority, double extra) {
            boolean shrinking = extra < 0;
            List<Integer> adjusting = new ArrayList<Integer>();
            for (int i = 0; i < grow.length; i++) {
                if (percent[i] < 0 && (shrinking || grow[i] == priority)) {
                    adjusting.add(i);
                }
            }
            double available = extra;
            boolean handleRemainder = false;
            double portion = 0;
            boolean wasPositive = available >= 0.0;
            boolean isPositive = wasPositive;
            Composite limits = shrinking ? minSizes() : maxSizes();
            while (available != 0 && wasPositive == isPositive && adjusting.size() > 0) {
                if (!handleRemainder) {
                    portion = available > 0 ? Math.floor(available / adjusting.size())
                            : Math.ceil(available / adjusting.size());
                }
                if (portion != 0) {
                    for (Iterator<Integer> it = adjusting.iterator(); it.hasNext();) {
                        int index = it.next();
                        double limit = Math.round(limits.proportionalMinOrMax(index, shrinking)) - sizes.sizes[index];
                        if (shrinking && limit > 0 || !shrinking && limit < 0) {
                            limit = 0;
                        }
                        double change = Math.abs(limit) <= Math.abs(portion) ? limit : portion;
                        sizes.sizes[index] += change;
                        available -= change;
                        isPositive = available >= 0.0;
                        if (Math.abs(change) < Math.abs(portion)) {
                            it.remove();
                        }
                        if (available == 0) {
                            break;
                        }
                    }
                } else {
                    portion = (int) available % adjusting.size();
                    if (portion == 0) {
                        break;
                    }
                    portion = shrinking ? -1 : 1;
                    handleRemainder = true;
                }
            }
            return available;
        }
    }

    private static final class Interval implements Comparable<Interval> {
        final int begin;
        final int end;

        Interval(int begin, int end) {
            this.begin = begin;
            this.end = end;
        }

        @Override
        public int compareTo(Interval o) {
            return begin != o.begin ? begin - o.begin : end - o.end;
        }

        boolean contains(int position) {
            return begin <= position && position < end;
        }

        int size() {
            return end - begin;
        }
    }

    /** GridPane.CompositeSize: sizes of single cells plus minimum sizes of spanned intervals. */
    private static final class Composite {
        double[] sizes;
        private TreeMap<Interval, Double> multi;
        private BitSet preset;
        private final double[] fixedPercent;
        private final double totalFixedPercent;
        private final double gap;

        Composite(int capacity, double[] fixedPercent, double totalFixedPercent, double gap, double initial) {
            sizes = new double[capacity];
            java.util.Arrays.fill(sizes, initial);
            this.fixedPercent = fixedPercent;
            this.totalFixedPercent = totalFixedPercent;
            this.gap = gap;
        }

        Composite copy() {
            Composite copy = new Composite(0, fixedPercent, totalFixedPercent, gap, 0);
            copy.sizes = sizes.clone();
            copy.multi = multi == null ? null : new TreeMap<Interval, Double>(multi);
            copy.preset = preset;
            return copy;
        }

        void setPreset(int position, double size) {
            sizes[position] = size;
            if (preset == null) {
                preset = new BitSet(sizes.length);
            }
            preset.set(position);
        }

        boolean isPreset(int position) {
            return preset != null && preset.get(position);
        }

        void setMax(int position, double size) {
            sizes[position] = Math.max(sizes[position], size);
        }

        void setMaxMulti(int start, int end, double size) {
            if (multi == null) {
                multi = new TreeMap<Interval, Double>();
            }
            Interval interval = new Interval(start, end);
            Double current = multi.get(interval);
            multi.put(interval, current == null ? size : Math.max(size, current));
        }

        Iterable<Map.Entry<Interval, Double>> multiSizes() {
            return multi == null ? Collections.<Map.Entry<Interval, Double>>emptyList() : multi.entrySet();
        }

        double proportionalMinOrMax(int position, boolean min) {
            double result = sizes[position];
            if (!isPreset(position) && multi != null) {
                for (Interval i : multi.keySet()) {
                    if (i.contains(position)) {
                        double segment = multi.get(i) / i.size();
                        double proportional = segment;
                        for (int j = i.begin; j < i.end; j++) {
                            if (j != position && (min ? sizes[j] > segment : sizes[j] < segment)) {
                                proportional += segment - sizes[j];
                            }
                        }
                        result = min ? Math.max(result, proportional) : Math.min(result, proportional);
                    }
                }
            }
            return result;
        }

        double total(int from, int to) {
            double total = gap * (to - from - 1);
            for (int i = from; i < to; i++) {
                total += sizes[i];
            }
            return total;
        }

        double total() {
            return total(0, sizes.length);
        }

        private boolean allPreset(int begin, int end) {
            if (preset == null) {
                return false;
            }
            for (int i = begin; i < end; i++) {
                if (!preset.get(i)) {
                    return false;
                }
            }
            return true;
        }

        double totalWithMultiSize() {
            double total = total();
            if (multi != null) {
                for (Map.Entry<Interval, Double> e : multi.entrySet()) {
                    Interval i = e.getKey();
                    if (!allPreset(i.begin, i.end)) {
                        double subTotal = total(i.begin, i.end);
                        if (e.getValue() > subTotal) {
                            total += e.getValue() - subTotal;
                        }
                    }
                }
            }
            if (totalFixedPercent > 0) {
                double totalNotFixed = 0;
                for (int i = 0; i < fixedPercent.length; i++) {
                    if (fixedPercent[i] == 0) {
                        total -= sizes[i];
                    }
                }
                for (int i = 0; i < fixedPercent.length; i++) {
                    if (fixedPercent[i] > 0) {
                        total = Math.max(total, sizes[i] * (100 / fixedPercent[i]));
                    } else if (fixedPercent[i] < 0) {
                        totalNotFixed += sizes[i];
                    }
                }
                if (totalFixedPercent < 100) {
                    total = Math.max(total, totalNotFixed * 100 / (100 - totalFixedPercent));
                }
            }
            return total;
        }
    }
}
