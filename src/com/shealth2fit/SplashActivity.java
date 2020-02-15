package com.shealth2fit;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_splash);
    new Handler().postDelayed(() -> {
      Intent i = new Intent(getApplicationContext(), MainActivity.class);
      startActivity(i);
      finish();
    }, 1000);
  }
}
