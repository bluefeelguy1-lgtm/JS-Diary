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
import com.google.firebase.firestore.FirebaseFirestore;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.UUID;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;

public class MainActivity extends Activity {
  private WebView web;
  private FirebaseAuth auth;
  private final FirebaseFirestore db = FirebaseFirestore.getInstance();
  private void js(String script){runOnUiThread(()->{if(web!=null)web.evaluateJavascript(script,null);});}
  public class DiaryBridge {
    @JavascriptInterface public void load(){
      if(auth.getCurrentUser()==null)return;
      String uid=auth.getCurrentUser().getUid();
      db.collection("users").document(uid).collection("private").document("diaryData").get().addOnSuccessListener(doc->{
        String value=doc.getString("json");
        if(value==null) js("window.cloudLoaded(null)");
        else js("window.cloudLoaded("+JSONObject.quote(value)+")");
      }).addOnFailureListener(e->js("window.cloudError("+JSONObject.quote(e.getMessage())+")"));
    }
    @JavascriptInterface public void save(String json){
      if(auth.getCurrentUser()==null || json.length()>750000)return;
      try {new JSONObject(json);}catch(Exception e){return;}
      String uid=auth.getCurrentUser().getUid();
      Map<String,Object> doc=new HashMap<>();doc.put("json",json);
      db.collection("users").document(uid).collection("private").document("diaryData").set(doc)
        .addOnSuccessListener(v->js("window.cloudSaved()"))
        .addOnFailureListener(e->js("window.cloudError("+JSONObject.quote(e.getMessage())+")"));
    }
  }
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
    auth = FirebaseAuth.getInstance();
    if (auth.getCurrentUser() == null) showLogin(); else showDiary();
  }
  private void showLogin() {
    if(web!=null){web.destroy();web=null;}
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
    Button reset = new Button(this);reset.setText("비밀번호를 잊으셨나요?");layout.addView(reset);
    reset.setOnClickListener(v->{String address=email.getText().toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){email.setError("이메일을 먼저 입력하세요");return;}auth.sendPasswordResetEmail(address).addOnSuccessListener(x->Toast.makeText(this,"비밀번호 재설정 메일을 보냈습니다",Toast.LENGTH_LONG).show()).addOnFailureListener(e->Toast.makeText(this,"요청을 처리하지 못했습니다: "+e.getLocalizedMessage(),Toast.LENGTH_LONG).show());});
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
  private String coupleKey(){return "couple_"+auth.getCurrentUser().getUid();}
  private String coupleId(){return getPreferences(MODE_PRIVATE).getString(coupleKey(),null);}
  private void saveCouple(String id){getPreferences(MODE_PRIVATE).edit().putString(coupleKey(),id).apply();}
  private void showSharedDiary(){
    String id=coupleId();
    if(id==null){showCoupleDialog();return;}
    db.collection("couples").document(id).get().addOnSuccessListener(doc->{
      if(!doc.exists() || !(doc.get("members") instanceof java.util.List)
        || !((java.util.List<?>)doc.get("members")).contains(auth.getCurrentUser().getUid())){
        saveCouple(null);Toast.makeText(this,"커플 연결을 다시 확인해 주세요",Toast.LENGTH_LONG).show();return;
      }
      LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,16,24,16);
      EditText title=new EditText(this);title.setHint("공유 일기 제목");root.addView(title);
      EditText body=new EditText(this);body.setHint("둘이 함께 볼 이야기");body.setMinLines(3);root.addView(body);
      Button add=new Button(this);add.setText("공유 일기 올리기");root.addView(add);
      android.widget.ScrollView scroll=new android.widget.ScrollView(this);
      LinearLayout entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);scroll.addView(entries);
      root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
      AlertDialog dialog=new AlertDialog.Builder(this).setTitle("♡ 우리의 공유 일기").setView(root).setPositiveButton("닫기",null).create();
      Runnable refresh=()->db.collection("couples").document(id).collection("entries").orderBy("createdAt",Query.Direction.DESCENDING).limit(50).get()
        .addOnSuccessListener(result->{entries.removeAllViews();for(DocumentSnapshot item:result){
          TextView row=new TextView(this);
          row.setText("♥ "+item.getString("title")+"\n"+item.getString("text")+"\n");
          row.setTextSize(16);row.setPadding(8,14,8,14);entries.addView(row);
        }}).addOnFailureListener(e->Toast.makeText(this,"공유 일기를 불러오지 못했습니다: "+e.getMessage(),Toast.LENGTH_LONG).show());
      add.setOnClickListener(v->{
        if(body.getText().toString().trim().isEmpty()){body.setError("내용을 입력하세요");return;}
        Map<String,Object> entry=new HashMap<>();
        entry.put("authorUid",auth.getCurrentUser().getUid());entry.put("title",title.getText().toString());
        entry.put("text",body.getText().toString());entry.put("type","diary");
        entry.put("createdAt",com.google.firebase.Timestamp.now());
        db.collection("couples").document(id).collection("entries").add(entry).addOnSuccessListener(ref->{
          title.setText("");body.setText("");refresh.run();
        }).addOnFailureListener(e->Toast.makeText(this,"저장 실패: "+e.getMessage(),Toast.LENGTH_LONG).show());
      });
      dialog.show();refresh.run();
    }).addOnFailureListener(e->Toast.makeText(this,"연결 확인 실패: "+e.getMessage(),Toast.LENGTH_LONG).show());
  }
  private void showCoupleDialog(){
    String existing=coupleId();
    if(existing!=null){new AlertDialog.Builder(this).setTitle("커플 초대 코드").setMessage(existing+"\n상대방에게 이 코드를 전달하세요.").setNeutralButton("코드 복사",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("JS다이어리 커플 코드",existing));Toast.makeText(this,"초대 코드 복사 완료",Toast.LENGTH_SHORT).show();}).setPositiveButton("확인",null).show();return;}
    new AlertDialog.Builder(this).setTitle("커플 연결").setItems(new String[]{"초대 코드 만들기","상대방 코드로 연결"},(d,which)->{
      String uid=auth.getCurrentUser().getUid();
      if(which==0){
        String id=UUID.randomUUID().toString();
        Map<String,Object> couple=new HashMap<>();couple.put("members",Arrays.asList(uid));couple.put("createdBy",uid);
        db.collection("couples").document(id).set(couple).addOnSuccessListener(v->{saveCouple(id);showCoupleDialog();})
          .addOnFailureListener(e->Toast.makeText(this,"코드 생성 실패: "+e.getMessage(),Toast.LENGTH_LONG).show());
      }else{
        EditText input=new EditText(this);input.setHint("상대방의 초대 코드");
        new AlertDialog.Builder(this).setTitle("초대 코드 입력").setView(input).setNegativeButton("취소",null).setPositiveButton("연결",(dialog,w)->{
          String id=input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
          if(!id.matches("[0-9a-f-]{36}")){Toast.makeText(this,"초대 코드 형식이 올바르지 않습니다",Toast.LENGTH_LONG).show();return;}
          db.runTransaction(transaction->{
            com.google.firebase.firestore.DocumentReference ref=db.collection("couples").document(id);
            com.google.firebase.firestore.DocumentSnapshot doc=transaction.get(ref);
            if(!doc.exists())throw new IllegalStateException("초대 코드가 존재하지 않습니다");
            java.util.List<String> members=(java.util.List<String>)doc.get("members");
            if(members==null||members.size()!=1||members.contains(uid))throw new IllegalStateException("이미 연결된 코드입니다");
            transaction.update(ref,"members",Arrays.asList(members.get(0),uid));
            return null;
          }).addOnSuccessListener(v->{saveCouple(id);Toast.makeText(this,"커플 연결 완료",Toast.LENGTH_LONG).show();})
          .addOnFailureListener(e->Toast.makeText(this,"연결 실패: "+e.getMessage(),Toast.LENGTH_LONG).show());
        }).show();
      }
    }).show();
  }
  private void showDiary() {
    LinearLayout layout = new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
    Button account = new Button(this); account.setText("계정: "+auth.getCurrentUser().getEmail()+"  ·  로그아웃");layout.addView(account);
    account.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("로그아웃할까요? 이 기기의 기존 일기 데이터는 삭제되지 않습니다.").setNegativeButton("취소",null).setPositiveButton("로그아웃",(d,w)->{auth.signOut();showLogin();}).show());
    if(web!=null){web.destroy();web=null;}
    Button couple = new Button(this);couple.setText("♡ 커플 연결 / 초대 코드");layout.addView(couple);couple.setOnClickListener(v->showCoupleDialog());
    Button shared=new Button(this);shared.setText("♡ 공유 일기 쓰기 / 보기");layout.addView(shared);shared.setOnClickListener(v->showSharedDiary());
    web = new WebView(this);
    web.addJavascriptInterface(new DiaryBridge(),"DiaryCloud");
    web.setWebViewClient(new WebViewClient());
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);
    s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
    layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(layout);web.loadUrl("file:///android_asset/index.html?uid="+android.net.Uri.encode(auth.getCurrentUser().getUid()));
  }
  @Override public void onBackPressed(){if(web!=null && web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
