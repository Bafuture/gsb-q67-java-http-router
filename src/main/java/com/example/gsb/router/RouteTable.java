package com.example.gsb.router;

import java.util.List;

/**
 * Renders the registered routes as an aligned plain-text table, ordered by matching
 * priority (most specific route first).
 */
public final class RouteTable {

    private RouteTable() {
    }

    public static String render(List<Route> routes) {
        String[] headers = {"PRIORITY", "METHOD", "PATTERN", "HANDLER"};
        String[][] rows = new String[routes.size()][];
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            rows[i] = new String[]{
                    String.valueOf(i + 1),
                    route.method().name(),
                    route.pattern().source(),
                    route.handlerName()
            };
        }
        int[] widths = new int[headers.length];
        for (int c = 0; c < headers.length; c++) {
            widths[c] = headers[c].length();
            for (String[] row : rows) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }
        StringBuilder table = new StringBuilder();
        appendRow(table, headers, widths);
        appendSeparator(table, widths);
        for (String[] row : rows) {
            appendRow(table, row, widths);
        }
        return table.toString();
    }

    private static void appendRow(StringBuilder table, String[] cells, int[] widths) {
        table.append("|");
        for (int c = 0; c < cells.length; c++) {
            table.append(' ').append(cells[c]);
            table.append(" ".repeat(widths[c] - cells[c].length()));
            table.append(" |");
        }
        table.append(System.lineSeparator());
    }

    private static void appendSeparator(StringBuilder table, int[] widths) {
        table.append("|");
        for (int width : widths) {
            table.append("-".repeat(width + 2)).append("|");
        }
        table.append(System.lineSeparator());
    }
}
