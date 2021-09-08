package org.tensorflow.lite.examples.detection.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.widget.Toast;

public class Utilities {

    private static Toast currentToast = null;
    private static ProgressDialog progressDialog;

    public static void showToast(Context context, String text)
    {
        if(currentToast != null) {
            currentToast.cancel();
        }

        try {
            Spannable centeredText = new SpannableString(text);
            centeredText.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    0, text.length() - 1,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            currentToast = Toast.makeText(context, centeredText, Toast.LENGTH_SHORT);
            currentToast.show();
        }
        catch (Exception e) {
            currentToast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
            currentToast.show();
        }
    }


    public static void toggleProgressDialogue(boolean showDialogue, Context context) {

        if(showDialogue) {

            // If dialog exists close it
            if(progressDialog != null) {
                progressDialog.dismiss();
            }

            // Setup progress dialog and display it
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Please wait...");
            progressDialog.setIndeterminate(true);
            progressDialog.setProgress(0);
            progressDialog.setMax(100);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setCancelable(false);

            // Remove number and percent text
            progressDialog.setProgressNumberFormat(null);
            progressDialog.setProgressPercentFormat(null);
            progressDialog.show();
        }

        else {

            // If dialog exists close it
            if(progressDialog != null) {
                progressDialog.dismiss();
            }
        }
    }
}
