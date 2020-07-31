package com.fizzbuzz.fizzbuzz;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private int number = 0; //初期数字
    private String resultNumber; //結果の数字
    private final String FIZZ = "fizz", BUZZ = "buzz", FIZZBUZZ = "FizzBuzz"; //Fizz or Buzz or FizzBuzz
    private Button incrementButton; //+1するボタン
    private Button zeroButton; //結果を０に戻すボタン
    private TextView resultTextView; //結果を表示するテキスト

    /**
     * 与えられたnumberが3の倍数の時"fizz",5の倍数の時"buzz",15の倍数の時"FizzBuzz"を引数として返す
     * @param number
     * @return fizz or buzz or fizzbuzz or number
     */
    private String fizzbuzz(int number){
        if(number % 3 == 0 && number % 5 == 0){
            return FIZZBUZZ;
        }else if(number % 3 == 0){
            return FIZZ;
        }else if(number % 5 == 0){
            return BUZZ;
        }else{
            return String.valueOf(number);
        }
    }

    /**
     * numberを0にリセットする
     */
    private void reset(){
        resultTextView.setText(String.valueOf(number=0));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // フィールドの初期化
        incrementButton = findViewById(R.id.increment_button); //+1するボタン
        resultTextView = findViewById(R.id.result_text_view); //結果を表示するテキスト
        zeroButton = findViewById(R.id.back_to_zero_button); //結果を0に戻すボタン
        resultTextView.setText(String.valueOf(number)); //初期値０を表示

         /*
           ボタンを押すと0から順に1づつ結果を表示する
           結果が3の倍数の時"Fizz",５の倍数の時"Buzz",１５の倍数の時"FizzBuzz"を代わりに表示する
          */
        incrementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                number++;
                resultTextView.setText(fizzbuzz(number));
            }
        });

        /*
        ボタンを押すと結果が０になる。
         */
        zeroButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                reset();
            }
        });

    }
}

