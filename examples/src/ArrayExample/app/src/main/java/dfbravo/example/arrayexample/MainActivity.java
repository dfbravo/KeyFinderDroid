package dfbravo.example.arrayexample;

import android.app.Activity;
import android.os.Bundle;

import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String keys[] = {"thisiskey1", "thisiskey2"};
        int i = 0+1;
        SecretKeySpec key = new SecretKeySpec(keys[i].getBytes(), "AES");
    }
}
