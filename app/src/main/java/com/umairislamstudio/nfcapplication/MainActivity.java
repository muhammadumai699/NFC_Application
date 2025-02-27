package com.umairislamstudio.nfcapplication;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.devnied.emvnfccard.model.EmvCard;
import com.github.devnied.emvnfccard.parser.EmvTemplate;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {


    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFiltersArray;

    private TextView txtCardNumber, txtExpiryDate, txtCardType, txtStatus;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        initViews();

        IntentFilter techDiscovered = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        IntentFilter ndefDiscovered = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        IntentFilter tagDiscovered = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);

        intentFiltersArray = new IntentFilter[]{techDiscovered, ndefDiscovered, tagDiscovered};

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE);


    }

    private void initViews() {
        txtCardNumber = findViewById(R.id.txtCardNumber);
        txtExpiryDate = findViewById(R.id.txtExpiryDate);
        txtCardType = findViewById(R.id.txtCardType);
        txtStatus = findViewById(R.id.txtStatus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            String[][] techListsArray = new String[][]{
                    new String[]{IsoDep.class.getName()}
            };
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Log.d("NFC_DEBUG", "NFC intent received: " + intent.getAction());

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                byte[] tagId = tag.getId();
                Log.d("NFC_DEBUG", "Tag detected: " + bytesToHex(tagId));
                String[] techList = tag.getTechList();
                StringBuilder techs = new StringBuilder("Supported NFC Techs:\n");
                for (String tech : techList) {
                    techs.append(tech).append("\n");
                }
                Log.d("NFC_DEBUG", techs.toString());

                IsoDep isoDep = IsoDep.get(tag);
                if (isoDep != null) {
                    txtStatus.setText("Reading card...");
                    readEmvCard(isoDep);
                } else {
                    Log.e("NFC_DEBUG", "IsoDep is null. The tag might not be an EMV card.");
                }
            } else {
                Log.e("NFC_DEBUG", "Tag is null.");
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void readEmvCard(IsoDep isoDep) {
        new Thread(() -> {
            try {
                isoDep.connect();

                // Set up the provider for NFC communication
                Provider provider = new Provider();
                provider.setmTagCom(isoDep);

                // Parse EMV card data
                EmvTemplate parser = EmvTemplate.Builder().setProvider(provider).build();
                EmvCard card = parser.readEmvCard();

                if (card != null) {
                    String cardNumber = maskCardNumber(card.getCardNumber());
                    String expiryDate = card.getExpireDate() != null ? card.getExpireDate().toString() : "Unknown";
                    String cardType = card.getType() != null ? card.getType().getName() : "Unknown";

                    Log.d("NFC", "Card Number: " + cardNumber);
                    Log.d("NFC", "Expiration Date: " + expiryDate);
                    Log.d("NFC", "Card Type: " + cardType);

                    // Update UI safely on the main thread
                    uiHandler.post(() -> {
                        txtCardNumber.setText("Card Number: " + cardNumber);
                        txtExpiryDate.setText("Expiry Date: " + expiryDate);
                        txtCardType.setText("Card Type: " + cardType);
                        txtStatus.setText("Card read successfully!");
                    });
                }

                isoDep.close();
            } catch (IOException e) {
                e.printStackTrace();
                uiHandler.post(() -> txtStatus.setText("Error reading card"));
            }
        }).start();
    }


    private String maskCardNumber(String cardNumber) {
        if (cardNumber != null && cardNumber.length() > 4) {
            return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
        }
        return "Invalid";
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }


}