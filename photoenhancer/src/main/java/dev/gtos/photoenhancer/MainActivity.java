package dev.gtos.photoenhancer;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density); layout.setPadding(padding, padding * 2, padding, padding);
        TextView title = new TextView(this); title.setText(R.string.app_name); title.setTextSize(28); layout.addView(title);
        TextView about = new TextView(this); about.setText("Natural photo reconstruction, entirely on your phone.\n\nEnhanced copies appear in Gallery after processing. Every original is retained. Fine details are estimates; this cannot recover information the camera never captured.\n\nSupports standard JPEG photos up to 12.5 MP. Ultra HDR and larger originals stay untouched."); about.setTextSize(17); layout.addView(about);
        android.widget.Switch enabled = new android.widget.Switch(this); enabled.setText(R.string.automatic); enabled.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enabled", true));
        enabled.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("enabled", checked).apply()); layout.addView(enabled);
        android.widget.Switch advanced = new android.widget.Switch(this); advanced.setText(R.string.advanced);
        advanced.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("advanced", true));
        advanced.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("advanced", checked).apply()); layout.addView(advanced);
        TextView reconstructionNote = new TextView(this); reconstructionNote.setText(R.string.reconstruction_note); layout.addView(reconstructionNote);
        Button choose = new Button(this); choose.setText(R.string.choose_photo); choose.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/jpeg").addCategory(Intent.CATEGORY_OPENABLE), 1)); layout.addView(choose);
        TextView status = new TextView(this); status.setText(getSharedPreferences("settings", MODE_PRIVATE).getString("last_result", "Ready. Originals are always preserved.")); layout.addView(status);
        setContentView(layout);
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            if (uri != null) enhance(uri);
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1 && result == RESULT_OK && data != null && data.getData() != null) enhance(data.getData());
    }
    private void enhance(Uri uri) {
        if (!"content".equals(uri.getScheme())) return;
        startForegroundService(new Intent(this, EnhanceService.class).setAction(EnhanceService.ACTION).setData(uri).putExtra("manual", true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        Toast.makeText(this, "Enhancing on your phone. Original retained.", Toast.LENGTH_LONG).show();
    }
}
