package com.fpt.csd201.servlet;

import com.fpt.csd201.benchmark.BenchmarkRunner;
import com.fpt.csd201.benchmark.BenchmarkRunner.BenchmarkResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * BenchmarkServlet — Handles RQ1, RQ2, RQ3 benchmark execution.
 *
 * Receives a POST with { "rq": "RQ1" | "RQ2" | "RQ3" }
 * and returns JSON benchmark results.
 *
 * All benchmark numbers come from actual code execution,
 * not from pre-computed or fake data.
 */
@WebServlet(urlPatterns = "/api/benchmark")
public class BenchmarkServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        // Read JSON body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(sb.toString()).getAsJsonObject();
        } catch (Exception e) {
            sendError(response, "Invalid JSON request body.");
            return;
        }

        String rq = json.has("rq") ? json.get("rq").getAsString().toUpperCase() : "";

        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("success", true);
        responseData.put("rq", rq);

        try {
            BenchmarkRunner runner = new BenchmarkRunner();
            List<BenchmarkResult> results;
            String description;

            switch (rq) {
                case "RQ1":
                    results = runner.runRQ1();
                    description = "RQ1: BoundedStackHistory (limit=500) vs LRUStackHistory (maxMB=50). "
                            + "Tests at 500, 1000, 2000 operations. "
                            + "Measures memory footprint and undo levels retained.";
                    break;
                case "RQ2":
                    results = runner.runRQ2();
                    description = "RQ2: CommandHistory (Operation objects) vs SnapshotHistory "
                            + "(full text snapshots) on 10MB text. "
                            + "Checks if Command Pattern keeps memory under 50MB.";
                    break;
                case "RQ3":
                    results = runner.runRQ3();
                    description = "RQ3: TwoStackHistory vs DequeHistory. "
                            + "Compares average Undo/Redo time over 1000 operations "
                            + "using System.nanoTime().";
                    break;
                default:
                    sendError(response, "Unknown research question: " + rq
                            + ". Use RQ1, RQ2, or RQ3.");
                    return;
            }

            List<Map<String, Object>> resultMaps = new ArrayList<>();
            for (BenchmarkResult r : results) {
                resultMaps.add(r.toMap());
            }

            responseData.put("description", description);
            responseData.put("results", resultMaps);
            responseData.put("message", "Benchmark completed");

        } catch (Exception e) {
            responseData.put("success", false);
            responseData.put("message", "Benchmark error: " + e.getMessage());
        }

        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(responseData));
        }
    }

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("message", message);
        try (PrintWriter out = response.getWriter()) {
            out.print(new Gson().toJson(err));
        }
    }
}
