package dfbravo.example.stringbuilderexample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SecretKeySpec key = new SecretKeySpec(generateKey().getBytes(), "AES");
    }

    protected String generateKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("thisisthekey");
        return sb.toString();
    }
}
