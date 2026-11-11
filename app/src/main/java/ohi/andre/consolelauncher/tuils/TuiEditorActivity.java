package ohi.andre.consolelauncher.tuils;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.R;

public class TuiEditorActivity extends Activity {

    public static final String FILE_PATH_KEY = "file_path";

    private EditText editorEt;
    private TextView filenameTv;
    private File currentFile;

    // Syntax Highlighting Patterns
    private static final Pattern SHEBANG_PATTERN = Pattern.compile("^#!.*$", Pattern.MULTILINE);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(#|//).*$", Pattern.MULTILINE);
    private static final Pattern VARIABLE_ASSIGN_PATTERN = Pattern.compile("(?m)^\\s*(\\w+)\\s*=");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{.*?\\}\\}");
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile("\\{\\{.*?\\}\\};");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tui_editor_layout);

        editorEt = findViewById(R.id.tui_editor_et);
        filenameTv = findViewById(R.id.editor_filename_tv);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnDry = findViewById(R.id.btn_dry_run);
        Button btnRun = findViewById(R.id.btn_run);

        String path = getIntent().getStringExtra(FILE_PATH_KEY);
        if (path != null) {
            currentFile = new File(path);
            filenameTv.setText(currentFile.getName());
            loadFile();
        }

        editorEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                highlightSyntax(s);
            }
        });

        btnSave.setOnClickListener(v -> saveFile());

        btnDry.setOnClickListener(v -> {
            saveFile();
            List<String> contentLines = new ArrayList<>();
            String[] split = editorEt.getText().toString().split("\n");
            for (String s1 : split) contentLines.add(s1);
            
            List<String> issues = TuiScriptEngine.validateSyntax(contentLines);
            if (issues.isEmpty()) {
                Toast.makeText(this, "Syntax OK: Ready to run.", Toast.LENGTH_SHORT).show();
            } else {
                StringBuilder sb = new StringBuilder("Syntax Issues Found:\n");
                for (String issue : issues) sb.append("- ").append(issue).append("\n");
                Tuils.sendOutput(this, sb.toString());
                Toast.makeText(this, "Syntax check completed with issues.", Toast.LENGTH_LONG).show();
            }
        });

        btnRun.setOnClickListener(v -> {
            saveFile();
            TuiScriptEngine.executeScriptFile(this, currentFile);
            finish();
        });
    }

    private void loadFile() {
        if (currentFile == null || !currentFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(currentFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            editorEt.setText(sb.toString());
            highlightSyntax(editorEt.getText());
        } catch (Exception e) {
            Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveFile() {
        if (currentFile == null) return;
        try (FileWriter fw = new FileWriter(currentFile)) {
            fw.write(editorEt.getText().toString());
            Toast.makeText(this, "Saved: " + currentFile.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void highlightSyntax(Editable s) {
        // Clear previous spans
        Object[] oldSpans = s.getSpans(0, s.length(), Object.class);
        for (Object span : oldSpans) {
            if (span instanceof ForegroundColorSpan || span instanceof StyleSpan) {
                s.removeSpan(span);
            }
        }

        // Apply Highlighting - Pro Colors
        applyPattern(s, SHEBANG_PATTERN, Color.parseColor("#5E81AC"), true); // Nordic Blue
        applyPattern(s, COMMENT_PATTERN, Color.parseColor("#616E88"), false); // Grey
        applyPattern(s, VARIABLE_ASSIGN_PATTERN, Color.parseColor("#A3BE8C"), false); // Green
        applyPattern(s, PLACEHOLDER_PATTERN, Color.parseColor("#88C0D0"), false); // Cyan
        applyPattern(s, COMMAND_INJECTION_PATTERN, Color.parseColor("#B48EAD"), true); // Purple
    }

    private void applyPattern(Spannable s, Pattern pattern, int color, boolean bold) {
        Matcher matcher = pattern.matcher(s);
        while (matcher.find()) {
            s.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (bold) {
                s.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
}
