package so.brendan.unicodeless;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;


public class Detector extends ActionBarActivity {
    private Button mButton;
    private EditText mOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detector);

        mOutput = (EditText) findViewById(R.id.output);
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOutput.getText().clear();
                new ScanTask().execute();
            }
        });

        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText("Missing glyph: \ue000");
    }

    public static final Paint PAINT;
    public static final byte[] MISSING_CHAR;

    static {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        PAINT = paint;
    }

    static public Bitmap createBitmap() {
        return Bitmap.createBitmap(64, 64, Bitmap.Config.ALPHA_8);
    }

    static public ByteBuffer createBuffer(Bitmap b) {
        return ByteBuffer.allocate(b.getByteCount());
    }

    static public Bitmap drawBitmap(Bitmap b, String text) {
       //b = createBitmap();
        Canvas c = new Canvas(b);
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        c.drawText(text, 0, 32, PAINT);
        return b;
    }

    static public byte[] getPixels(ByteBuffer buffer, Bitmap b) {
        buffer.rewind();//clear();
        //buffer = createBuffer(b);
        b.copyPixelsToBuffer(buffer);
        return buffer.array();
    }

    static public boolean isCharacterMissing(Bitmap bmp, ByteBuffer buf, String ch) {
        return Arrays.equals(getPixels(buf, drawBitmap(bmp, ch)), MISSING_CHAR);
    }

    static public boolean isCharacterMissing(String ch) {
        Bitmap bmp = createBitmap();
        ByteBuffer buf = createBuffer(bmp);

        return isCharacterMissing(bmp, buf, ch);
    }

    static {
        Bitmap bmp = createBitmap();
        ByteBuffer buf = createBuffer(bmp);
        MISSING_CHAR = getPixels(buf, drawBitmap(bmp, "\uE000"));
    }

    static public final int LAST_CODEPOINT = 0x1F700;//0xE01EF;
    static public final int PRIVATE_USE_START = 0xE000;
    static public final int PRIVATE_USE_END = 0xF8FF;

    class ScanTask extends AsyncTask<Void, String, Void> {
        private ProgressDialog mProgress;
        private long mLast = 0;
        private long mC = 0;

        private Bitmap mBmp;
        private ByteBuffer mBuf;

        private String outputValues() {
            String out;

            if (mLast == -1) {
                mLast = 0;
            }

            if (mC == 0) {
                out = Long.toHexString(mLast);
            } else {
                out = Long.toHexString(mLast) + "-" + Long.toHexString(mLast + mC);
            }

            return out;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();

            mOutput.getText().append("Cancelled!");
        }

        @Override
        protected Void doInBackground(Void... params) {
            mLast = -1;
            mC = 0;

            mBmp = createBitmap();
            mBuf = createBuffer(mBmp);

            for (int i = 0; i <= LAST_CODEPOINT; ++i) {
                if (isCancelled()) {
                    return null;
                }

                if (i == PRIVATE_USE_START) {
                    i = PRIVATE_USE_END;
                    continue;
                }

                String s = new String(Character.toChars(i));
                String is = Integer.toHexString(i);

                if (!isCharacterMissing(mBmp, mBuf, s)) {
                    if (mLast + mC + 1 == i) {
                        mC++;
                        mProgress.incrementProgressBy(1);
                        publishProgress(is + " (" + mC + ")");
                    } else {
                        publishProgress(is, outputValues());
                        mC = 0;
                        mLast = i;
                    }
                } else {
                    publishProgress(is);
                }
            }

            publishProgress("Done!", outputValues());

            File file = new File(getFilesDir(), "valid_codepoints.txt");

            if (file.exists()) {
                file.delete();
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
                outputStream.write(mOutput.getText().toString().getBytes());
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);

            if (values.length >= 2) {
                mOutput.getText().append(values[1]).append(",");
            }

            mProgress.setMessage(values[0]);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            mProgress = new ProgressDialog(Detector.this);
            mProgress.setMax(LAST_CODEPOINT);
            mProgress.setProgress(0);
            mProgress.setTitle("Detecting codepoints...");
            mProgress.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    ScanTask.this.cancel(true);
                }
            });
            mProgress.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            mProgress.dismiss();
        }

    }
}
