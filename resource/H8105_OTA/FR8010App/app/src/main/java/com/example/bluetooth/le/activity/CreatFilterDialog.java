package com.example.bluetooth.le.activity;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.bluetooth.le.R;

class CreatFilterDialog extends Dialog {
   private View.OnClickListener mClickListener;
   private Context mContext;
   public EditText textName;
   public EditText textRssi;
   public Button btnOK;

   public CreatFilterDialog(@NonNull Context context) {
      super(context);
   }
   public CreatFilterDialog(@NonNull Context context, int theme, View.OnClickListener clickListener) {
      super(context);
      this.mContext = context;
      this.mClickListener = clickListener;
   }

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      this.setContentView(R.layout.listitem_device2);

      textName = (EditText) findViewById(R.id.edit_Name);
      textRssi = (EditText) findViewById(R.id.edit_Rssi);
      btnOK = (Button) findViewById(R.id.button);

      btnOK.setOnClickListener(mClickListener);
      this.setCancelable(true);
   }
}
