package com.jsdiary.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends Activity {
  private WebView web;
  private FirebaseAuth auth;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
    auth = FirebaseAuth.getInstance();
    if (auth.getCurrentUser() == null) showLogin(); else showDiary();
  }
  private void showLogin() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(40, 50, 40, 20);
    TextView title = new TextView(this);
    title.setText("♡ JS 다이어리\n각자의 계정으로 로그인하세요");
    title.setTextSize(22);
    layout.addView(title);
    EditText email = new EditText(this);
    email.setHint("이메일"); email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    layout.addView(email);
    EditText password = new EditText(this);
    password.setHint("비밀번호 (6자 이상)");
    password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    layout.addView(password);
    Button login = new Button(this); login.setText("로그인"); layout.addView(login);
    Button register = new Button(this); register.setText("새 계정 만들기"); layout.addView(register);
    setContentView(layout);
    login.setOnClickListener(v -> signIn(email.getText().toString().trim(),password.getText().toString(),false));
    register.setOnClickListener(v -> signIn(email.getText().toString().trim(),password.getText().toString(),true));
  }
  private void signIn(String email,String password,boolean register) {
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() || password.length()<6) {
      Toast.makeText(this,"올바른 이메일과 6자 이상의 비밀번호를 입력하세요",Toast.LENGTH_LONG).show();return;
    }
    if (register) auth.createUserWithEmailAndPassword(email,password).addOnCompleteListener(this,task->{
      if(task.isSuccessful()) showDiary();
      else Toast.makeText(this,"가입 실패: "+task.getException().getLocalizedMessage(),Toast.LENGTH_LONG).show();
    });
    else auth.signInWithEmailAndPassword(email,password).addOnCompleteListener(this,task->{
      if(task.isSuccessful()) showDiary();
      else Toast.makeText(this,"로그인 실패: "+task.getException().getLocalizedMessage(),Toast.LENGTH_LONG).show();
    });
  }
  private void showDiary() {
    LinearLayout layout = new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
    Button account = new Button(this); account.setText("계정: "+auth.getCurrentUser().getEmail()+"  ·  로그아웃");layout.addView(account);
    account.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("로그아웃할까요? 이 기기의 기존 일기 데이터는 삭제되지 않습니다.").setNegativeButton("취소",null).setPositiveButton("로그아웃",(d,w)->{auth.signOut();showLogin();}).show());
    web = new WebView(this);
    web.setWebViewClient(new WebViewClient());
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);
    s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
    layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(layout);web.loadUrl("file:///android_asset/index.html");
  }
  @Override public void onBackPressed(){if(web!=null && web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
