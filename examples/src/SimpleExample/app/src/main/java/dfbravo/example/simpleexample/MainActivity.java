package dfbravo.example.simpleexample;

import android.app.Activity;
import android.os.Bundle;

import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SecretKeySpec key1 = new SecretKeySpec("thisisthekey1".getBytes(), "AES");

        SecretKeySpec key2 = new SecretKeySpec(generateKey().getBytes(), "AES");
    }

    protected String generateKey() {
        String keyData = "thisisthekey2";
        return keyData.toString();
    }
}
