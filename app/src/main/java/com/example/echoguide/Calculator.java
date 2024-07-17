package com.example.echoguide;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class Calculator extends AppCompatActivity {

    private static final int REQ_CODE_SPEECH_INPUT = 100;

    private TextView resultTextView;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        resultTextView = findViewById(R.id.resultTextView);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show();
            }
        });

        startSpeechRecognition();
    }
    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak for the calculation");
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            ArrayList<String> voiceResults = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (voiceResults != null && !voiceResults.isEmpty()) {
                String voiceInput = voiceResults.get(0);
                updateInput(voiceInput);
                calculateResult();

                // After displaying the result, wait for 5 seconds and start taking for new input
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startSpeechRecognition();
                    }
                }, 3000); // Wait for 5 seconds after displaying the result, and restart the voice dialog for taking new input
            } else {

                Toast.makeText(this, "Failed to recognize voice", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private StringBuilder inputStringBuilder = new StringBuilder();
    private void calculateResult() {
        String input = inputStringBuilder.toString();

        try {
            Expression expression = new ExpressionBuilder(input).build();
            double result = expression.evaluate();
            resultTextView.setText(String.valueOf(result));
            // Convert result to speech
            speakResult(String.valueOf(result));

            // Convert result string to integer to speech For ex: 3.12 -> 3
            //resultTextView.setText(String.valueOf((int) result));  // Convert to integer
            //speakResult(String.valueOf((int) result));  // Speak integer result

        } catch (Exception e) {
            resultTextView.setText("Error");
            speakResult("Something Wrong Please Speak Again");
        }
    }


    // text to speech
    private void speakResult(String text) {
        // Use text-to-speech to speak the result
        //textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);


        // convert integer value section is start
        if (isNumeric(text)) {
            if (text.contains(".")) {
                String[] parts = text.split("\\.");
                String integerPart = convertNumberToWords(parts[0]);
                String decimalPart = convertDecimalToWords(parts[1]);
                textToSpeech.speak(integerPart + " point " + decimalPart, TextToSpeech.QUEUE_FLUSH, null);
            } else {
                textToSpeech.speak(convertNumberToWords(text), TextToSpeech.QUEUE_FLUSH, null);
            }
        } else {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private String convertNumberToWords(String number) {
        return number;
    }

    private String convertDecimalToWords(String decimal) {
        return decimal;
    }

    private boolean isNumeric(String str) {
        // Check if the string can be parsed as a number
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void updateInput(String input) {
        // Clear previous input
        inputStringBuilder.setLength(0);

        // Convert voice input to standard operators
        input = convertVoiceCommandToOperator(input);

        // Append new input
        inputStringBuilder.append(input);
        resultTextView.setText(inputStringBuilder.toString());
    }

    private String convertVoiceCommandToOperator(String voiceCommand) {
        // Convert spoken command to standard operator
        switch (voiceCommand.toLowerCase()) {
            case "plus":
            case "add":
                return "+";
            case "minus":
            case "subtract":
                return "-";
            case "times":
            case "multiply":
            case "multiply by":
                return "*";
            case "divided by":
            case "divide":
                return "/";
            default:
                return voiceCommand;
        }
    }

    // text to speech onDestroy method
    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}