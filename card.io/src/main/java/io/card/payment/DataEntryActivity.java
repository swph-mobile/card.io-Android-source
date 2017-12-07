package io.card.payment;

/* DataEntryActivity.java
 * See the file "LICENSE.md" for the full license governing this code.
 */

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DateKeyListener;
import android.text.method.DigitsKeyListener;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.card.payment.i18n.LocalizedStrings;
import io.card.payment.i18n.StringKey;
import io.card.payment.ui.ActivityHelper;
import io.card.payment.ui.Appearance;
import io.card.payment.ui.ViewUtil;

/**
 * Activity responsible for entry of Expiry, CVV, Postal Code (and card number in manual case).
 *
 * @version 2.0
 */
public final class DataEntryActivity extends Activity implements TextWatcher {

    /**
     * PayPal REST Apis only handle max 20 chars postal code, so we'll do the same here.
     */
    private static final int MAX_POSTAL_CODE_LENGTH = 20;
    /**
     * PayPal REST Apis accept max of 175 chars for cardholder name
     */
    private static final int MAX_CARDHOLDER_NAME_LENGTH = 175;
    private static final String PADDING_DIP = "4dip";
    private static final String LABEL_LEFT_PADDING_DEFAULT = "2dip";
    private static final String LABEL_LEFT_PADDING_HOLO = "12dip";

    private static final String FIELD_HALF_GUTTER = PADDING_DIP;

    private int viewIdCounter = 1;

    private static final int editTextIdBase = 100;

    private int editTextIdCounter = editTextIdBase;

    private TextView activityTitleTextView;
    private EditText numberEdit;
    private Validator numberValidator;
    private EditText expiryEdit;
    private Validator expiryValidator;
    private EditText cvvEdit;
    private Validator cvvValidator;
    private EditText postalCodeEdit;
    private Validator postalCodeValidator;
    private EditText cardholderNameEdit;
    private Validator cardholderNameValidator;
    private ImageView cardView;
    private Button doneBtn;
    private Button cancelBtn;
    private CreditCard capture;

    private boolean autoAcceptDone;
    private String labelLeftPadding;
    private boolean useApplicationTheme;
    private int defaultTextColor;

    private static final String TAG = DataEntryActivity.class.getSimpleName();

    @SuppressWarnings("ResourceType")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getIntent().getExtras()) {
            // extras should never be null.  This is some weird android state that we handle by just going back.
            onBackPressed();
            return;
        }

        useApplicationTheme = getIntent().getBooleanExtra(CardIOActivity.EXTRA_KEEP_APPLICATION_THEME, false);
        ActivityHelper.setActivityTheme(this, useApplicationTheme);

        defaultTextColor = new TextView(this).getTextColors().getDefaultColor();

        labelLeftPadding = ActivityHelper.holoSupported() ? LABEL_LEFT_PADDING_HOLO
                : LABEL_LEFT_PADDING_DEFAULT;

        LocalizedStrings.setLanguage(getIntent());

        int paddingPx = ViewUtil.typedDimensionValueToPixelsInt(PADDING_DIP, this);

        RelativeLayout container = new RelativeLayout(this);
        LayoutInflater inflater = getLayoutInflater();
        RelativeLayout titleView = (RelativeLayout) inflater.inflate(R.layout.cio_activity_card_scanner, container, false);
        container.setBackgroundColor(getResources().getColor(R.color.cio_bg_color));
        titleView.setBackgroundColor(getIntent().getIntExtra(CardIOActivity.EXTRA_TOOLBAR_COLOR, 0));

//        container.addView(titleView, titleParams);
        if( !useApplicationTheme ) {
            container.setBackgroundColor(Appearance.DEFAULT_BACKGROUND_COLOR);
        }

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setBackgroundColor(getResources().getColor(R.color.cio_bg_color));
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        RelativeLayout.LayoutParams mainParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        mainParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

        ActionBar actionBar = getActionBar();
        if (null != actionBar) {
            actionBar.hide();
        }

        capture = getIntent().getParcelableExtra(CardIOActivity.EXTRA_SCAN_RESULT);

        autoAcceptDone = getIntent().getBooleanExtra("debug_autoAcceptResult", false);

        if (capture != null) {
            numberValidator = new CardNumberValidator(capture.cardNumber);

            cardView = new ImageView(this);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

            cardParams.gravity = Gravity.CENTER_VERTICAL;
            cardParams.weight = 1;
            // static access is necessary, else we see weird crashes on some devices.
            cardView.setImageBitmap(io.card.payment.CardIOActivity.markedCardImage);

            mainLayout.addView(cardView, cardParams);

        } else {

            activityTitleTextView = new TextView(this);
            activityTitleTextView.setTextSize(24);
            if(! useApplicationTheme ) {
                activityTitleTextView.setTextColor(Appearance.PAY_BLUE_COLOR);
            }
            mainLayout.addView(activityTitleTextView);
            ViewUtil.setPadding(activityTitleTextView, null, null, null,
                    Appearance.VERTICAL_SPACING);
            ViewUtil.setDimensions(activityTitleTextView, LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT);

            LinearLayout numberLayout = new LinearLayout(this);
            numberLayout.setOrientation(LinearLayout.VERTICAL);
            ViewUtil.setPadding(numberLayout, null, PADDING_DIP, null, PADDING_DIP);

            TextView numberLabel = new TextView(this);
            ViewUtil.setPadding(numberLabel, labelLeftPadding, null, null, null);
            numberLabel.setText(LocalizedStrings.getString(StringKey.ENTRY_CARD_NUMBER));
            if(! useApplicationTheme ) {
                numberLabel.setTextColor(Appearance.TEXT_COLOR_LABEL);
            }
            numberLayout.addView(numberLabel, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            numberEdit = new EditText(this);
            numberEdit.setId(editTextIdCounter++);
            numberEdit.setMaxLines(1);
            numberEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
            numberEdit.setTextAppearance(getApplicationContext(),
                    android.R.attr.textAppearanceLarge);
            numberEdit.setInputType(InputType.TYPE_CLASS_PHONE);
            numberEdit.setHint("1234 5678 1234 5678");
            if(! useApplicationTheme ) {
                numberEdit.setHintTextColor(Appearance.TEXT_COLOR_EDIT_TEXT_HINT);
            }

            numberValidator = new CardNumberValidator();
            numberEdit.addTextChangedListener(numberValidator);
            numberEdit.addTextChangedListener(this);
            numberEdit.setFilters(new InputFilter[] { new DigitsKeyListener(), numberValidator });

            numberLayout.addView(numberEdit, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            mainLayout.addView(numberLayout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }

        LinearLayout optionLayout = new LinearLayout(this);
        LinearLayout.LayoutParams optionLayoutParam = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ViewUtil.setPadding(optionLayout, null, PADDING_DIP, null, PADDING_DIP);
        optionLayout.setOrientation(LinearLayout.HORIZONTAL);

        boolean requireExpiry = getIntent().getBooleanExtra(CardIOActivity.EXTRA_REQUIRE_EXPIRY, false);
        boolean requireCVV = getIntent().getBooleanExtra(CardIOActivity.EXTRA_REQUIRE_CVV, false);
        boolean requirePostalCode = getIntent().getBooleanExtra(CardIOActivity.EXTRA_REQUIRE_POSTAL_CODE, false);

        if (requireExpiry) {
            LinearLayout expiryLayout = new LinearLayout(this);
            LinearLayout.LayoutParams expiryLayoutParam = new LinearLayout.LayoutParams(0,
                    LayoutParams.MATCH_PARENT, 1);
            expiryLayout.setOrientation(LinearLayout.VERTICAL);

            TextView expiryLabel = new TextView(this);
            if(! useApplicationTheme ) {
                expiryLabel.setTextColor(Appearance.TEXT_COLOR_LABEL);
            }
            expiryLabel.setText(LocalizedStrings.getString(StringKey.ENTRY_EXPIRES));
            ViewUtil.setPadding(expiryLabel, labelLeftPadding, null, null, null);

            expiryLayout.addView(expiryLabel, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            expiryEdit = new EditText(this);
            expiryEdit.setId(editTextIdCounter++);
            expiryEdit.setMaxLines(1);
            expiryEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
            expiryEdit.setTextAppearance(getApplicationContext(),
                    android.R.attr.textAppearanceLarge);
            expiryEdit.setInputType(InputType.TYPE_CLASS_PHONE);
            expiryEdit.setHint(LocalizedStrings.getString(StringKey.EXPIRES_PLACEHOLDER));
            if(! useApplicationTheme ) {
                expiryEdit.setHintTextColor(Appearance.TEXT_COLOR_EDIT_TEXT_HINT);
            }

            if (capture != null) {
                expiryValidator = new ExpiryValidator(capture.expiryMonth, capture.expiryYear);
            } else {
                expiryValidator = new ExpiryValidator();
            }
            if (expiryValidator.hasFullLength()) {
                expiryEdit.setText(expiryValidator.getValue());
            }
            expiryEdit.addTextChangedListener(expiryValidator);
            expiryEdit.addTextChangedListener(this);
            expiryEdit.setFilters(new InputFilter[] { new DateKeyListener(), expiryValidator });

            expiryLayout.addView(expiryEdit, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            optionLayout.addView(expiryLayout, expiryLayoutParam);
            ViewUtil.setMargins(expiryLayout, null, null,
                    (requireCVV || requirePostalCode) ? FIELD_HALF_GUTTER : null, null);
        } else {
            expiryValidator = new AlwaysValid();
        }

        if (requireCVV) {
            LinearLayout cvvLayout = new LinearLayout(this);
            LinearLayout.LayoutParams cvvLayoutParam = new LinearLayout.LayoutParams(0,
                    LayoutParams.MATCH_PARENT, 1);
            cvvLayout.setOrientation(LinearLayout.VERTICAL);

            TextView cvvLabel = new TextView(this);
            if(! useApplicationTheme ) {
                cvvLabel.setTextColor(Appearance.TEXT_COLOR_LABEL);
            }
            ViewUtil.setPadding(cvvLabel, labelLeftPadding, null, null, null);
            cvvLabel.setText(LocalizedStrings.getString(StringKey.ENTRY_CVV));

            cvvLayout.addView(cvvLabel, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            cvvEdit = new EditText(this);
            cvvEdit.setId(editTextIdCounter++);
            cvvEdit.setMaxLines(1);
            cvvEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
            cvvEdit.setTextAppearance(getApplicationContext(), android.R.attr.textAppearanceLarge);
            cvvEdit.setInputType(InputType.TYPE_CLASS_PHONE);
            cvvEdit.setHint("123");
            if(! useApplicationTheme ) {
                cvvEdit.setHintTextColor(Appearance.TEXT_COLOR_EDIT_TEXT_HINT);
            }

            int length = 4;
            if (capture != null) {
                CardType type = CardType.fromCardNumber(numberValidator.getValue());
                length = type.cvvLength();
            }
            cvvValidator = new FixedLengthValidator(length);
            cvvEdit.setFilters(new InputFilter[] { new DigitsKeyListener(), cvvValidator });
            cvvEdit.addTextChangedListener(cvvValidator);
            cvvEdit.addTextChangedListener(this);

            cvvLayout.addView(cvvEdit, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            optionLayout.addView(cvvLayout, cvvLayoutParam);
            ViewUtil.setMargins(cvvLayout, requireExpiry ? FIELD_HALF_GUTTER : null, null,
                    requirePostalCode ? FIELD_HALF_GUTTER : null, null);
        } else {
            cvvValidator = new AlwaysValid();
        }

        if (requirePostalCode) {
            LinearLayout postalCodeLayout = new LinearLayout(this);
            LinearLayout.LayoutParams postalCodeLayoutParam = new LinearLayout.LayoutParams(0,
                    LayoutParams.MATCH_PARENT, 1);
            postalCodeLayout.setOrientation(LinearLayout.VERTICAL);

            TextView zipLabel = new TextView(this);
            if(! useApplicationTheme ) {
                zipLabel.setTextColor(Appearance.TEXT_COLOR_LABEL);
            }
            ViewUtil.setPadding(zipLabel, labelLeftPadding, null, null, null);
            zipLabel.setText(LocalizedStrings.getString(StringKey.ENTRY_POSTAL_CODE));

            postalCodeLayout
                    .addView(zipLabel, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            boolean postalCodeNumericOnly =
                    getIntent().getBooleanExtra(CardIOActivity.EXTRA_RESTRICT_POSTAL_CODE_TO_NUMERIC_ONLY, false);

            postalCodeEdit = new EditText(this);
            postalCodeEdit.setId(editTextIdCounter++);
            postalCodeEdit.setMaxLines(1);
            postalCodeEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
            postalCodeEdit.setTextAppearance(getApplicationContext(),
                    android.R.attr.textAppearanceLarge);
            if (postalCodeNumericOnly) {
                // class is phone to be consistent with other numeric fields.  Perhaps this could be improved.
                postalCodeEdit.setInputType(InputType.TYPE_CLASS_PHONE);
            } else {
                postalCodeEdit.setInputType(InputType.TYPE_CLASS_TEXT);
            }
            if(! useApplicationTheme ) {
                postalCodeEdit.setHintTextColor(Appearance.TEXT_COLOR_EDIT_TEXT_HINT);
            }

            postalCodeValidator = new MaxLengthValidator(MAX_POSTAL_CODE_LENGTH);
            postalCodeEdit.addTextChangedListener(postalCodeValidator);
            postalCodeEdit.addTextChangedListener(this);

            postalCodeLayout.addView(postalCodeEdit, LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
            optionLayout.addView(postalCodeLayout, postalCodeLayoutParam);
            ViewUtil.setMargins(postalCodeLayout, (requireExpiry || requireCVV) ? FIELD_HALF_GUTTER
                    : null, null, null, null);
        } else {
            postalCodeValidator = new AlwaysValid();
        }

        addCardholderNameIfNeeded(mainLayout);

        ViewUtil.setPadding(mainLayout, Appearance.CONTAINER_MARGIN_HORIZONTAL ,null,Appearance.CONTAINER_MARGIN_HORIZONTAL,null);
        container.addView(mainLayout, mainParams);
        container.addView(titleView);

        TextView reminderText = new TextView(this);
        reminderText.setText("Please verify card number.");
        reminderText.setTextColor(getResources().getColor(R.color.cio_text_color));
        reminderText.setTextSize(16.0f);
        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        textParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Do what you need for this SDK
            Window window = this.getWindow();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(getIntent().getIntExtra(CardIOActivity.EXTRA_TOOLBAR_COLOR, 0));
        };

        textParams.setMargins(0, 0, 0, displayMetrics.heightPixels/6);
        container.addView(reminderText, textParams);

        setContentView(container);

        Drawable icon = null;
        boolean usePayPalActionBarIcon = getIntent().getBooleanExtra(CardIOActivity.EXTRA_USE_PAYPAL_ACTIONBAR_ICON, true);
        if (usePayPalActionBarIcon) {
            //noinspection deprecation
            icon = getResources().getDrawable(R.drawable.cio_ic_paypal_monogram);
        }

        // update UI to reflect expiry validness
        if(requireExpiry && expiryValidator.isValid()){
            afterTextChanged(expiryEdit.getEditableText());
        }

        ImageButton mSaveUserDetailsButton = (ImageButton) titleView.findViewById(R.id.partial_toolbar_check_view);
        mSaveUserDetailsButton.setVisibility(View.VISIBLE);
        mSaveUserDetailsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                completed();
            }
        });

        ImageButton arrowView = (ImageButton) titleView.findViewById(R.id.partial_toolbar_arrow_view);
        arrowView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    private void completed() {
        if (capture == null) {
            capture = new CreditCard();
        }
        if (expiryEdit != null) {
            capture.expiryMonth = ((ExpiryValidator) expiryValidator).month;
            capture.expiryYear = ((ExpiryValidator) expiryValidator).year;
        }

        CreditCard result = new CreditCard(numberValidator.getValue(), capture.expiryMonth,
                capture.expiryYear, cvvValidator.getValue(), postalCodeValidator.getValue(),
                cardholderNameValidator.getValue());
        Intent dataIntent = new Intent();
        dataIntent.putExtra(CardIOActivity.EXTRA_SCAN_RESULT, result);
        if(getIntent().hasExtra(CardIOActivity.EXTRA_CAPTURED_CARD_IMAGE)){
            dataIntent.putExtra(CardIOActivity.EXTRA_CAPTURED_CARD_IMAGE,
                    getIntent().getByteArrayExtra(CardIOActivity.EXTRA_CAPTURED_CARD_IMAGE));
        }
        DataEntryActivity.this.setResult(CardIOActivity.RESULT_CARD_INFO, dataIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        DataEntryActivity.this.setResult(CardIOActivity.RESULT_ENTRY_CANCELED);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        ActivityHelper.setFlagSecure(this);

//        validateAndEnableDoneButtonIfValid();

        if (numberEdit == null && expiryEdit != null && !expiryValidator.isValid()) {
            expiryEdit.requestFocus();
        } else {
            advanceToNextEmptyField();
        }

        if (numberEdit != null || expiryEdit != null || cvvEdit != null || postalCodeEdit != null || cardholderNameEdit != null) {
            getWindow()
                    .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private EditText advanceToNextEmptyField() {
        int viewId = editTextIdBase;
        EditText et;
        while ((et = (EditText) findViewById(viewId++)) != null) {
            if (et.getText().length() == 0) {
                if (et.requestFocus()) {
                    return et;
                }
            }
        }
        // all fields have content
        return null;
    }

    private void validateAndEnableDoneButtonIfValid() {
        doneBtn.setEnabled(numberValidator.isValid() && expiryValidator.isValid()
                && cvvValidator.isValid() && postalCodeValidator.isValid()
                && cardholderNameValidator.isValid());

        if (autoAcceptDone && numberValidator.isValid() && expiryValidator.isValid()
                && cvvValidator.isValid() && postalCodeValidator.isValid()
                && cardholderNameValidator.isValid()) {
            completed();
        }
    }

    @Override
    public void afterTextChanged(Editable et) {

        if (numberEdit != null && et == numberEdit.getText()) {
            if (numberValidator.hasFullLength()) {
                if (!numberValidator.isValid()) {
                    numberEdit.setTextColor(Appearance.TEXT_COLOR_ERROR);
                } else {
                    setDefaultColor(numberEdit);
                    advanceToNextEmptyField();
                }
            } else {
                setDefaultColor(numberEdit);
            }

            if (cvvEdit != null) {
                CardType type = CardType.fromCardNumber(numberValidator.getValue().toString());
                FixedLengthValidator v = (FixedLengthValidator) cvvValidator;
                int length = type.cvvLength();
                v.requiredLength = length;
                cvvEdit.setHint(length == 4 ? "1234" : "123");
            }
        } else if (expiryEdit != null && et == expiryEdit.getText()) {
            if (expiryValidator.hasFullLength()) {
                if (!expiryValidator.isValid()) {
                    expiryEdit.setTextColor(Appearance.TEXT_COLOR_ERROR);
                } else {
                    setDefaultColor(expiryEdit);
                    advanceToNextEmptyField();
                }
            } else {
                setDefaultColor(expiryEdit);
            }
        } else if (cvvEdit != null && et == cvvEdit.getText()) {
            if (cvvValidator.hasFullLength()) {
                if (!cvvValidator.isValid()) {
                    cvvEdit.setTextColor(Appearance.TEXT_COLOR_ERROR);
                } else {
                    setDefaultColor(cvvEdit);
                    advanceToNextEmptyField();
                }
            } else {
                setDefaultColor(cvvEdit);
            }
        } else if (postalCodeEdit != null && et == postalCodeEdit.getText()) {
            if (postalCodeValidator.hasFullLength()) {
                if (!postalCodeValidator.isValid()) {
                    postalCodeEdit.setTextColor(Appearance.TEXT_COLOR_ERROR);
                } else {
                    setDefaultColor(postalCodeEdit);
                }
            } else {
                setDefaultColor(postalCodeEdit);
            }
        } else if (cardholderNameEdit != null && et == cardholderNameEdit.getText()) {
            if (cardholderNameValidator.hasFullLength()) {
                if (!cardholderNameValidator.isValid()) {
                    cardholderNameEdit.setTextColor(Appearance.TEXT_COLOR_ERROR);
                } else {
                    setDefaultColor(cardholderNameEdit);
                }
            } else {
                setDefaultColor(cardholderNameEdit);
            }
        }

        this.validateAndEnableDoneButtonIfValid();
    }

    private void setDefaultColor(EditText editText) {
        if (useApplicationTheme) {
            editText.setTextColor(defaultTextColor);
        } else {
            editText.setTextColor(Appearance.TEXT_COLOR_EDIT_TEXT);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
        // leave empty
    }

    @Override
    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
        // leave empty

    }

    @SuppressWarnings("ResourceType")
    private void addCardholderNameIfNeeded(ViewGroup mainLayout) {
        boolean requireCardholderName = getIntent().getBooleanExtra(CardIOActivity.EXTRA_REQUIRE_CARDHOLDER_NAME, false);
        if (requireCardholderName) {
            LinearLayout cardholderNameLayout = new LinearLayout(this);
            ViewUtil.setPadding(cardholderNameLayout, null, PADDING_DIP, null, null);
            cardholderNameLayout.setOrientation(LinearLayout.VERTICAL);

            TextView cardholderNameLabel = new TextView(this);
            if(! useApplicationTheme ) {
                cardholderNameLabel.setTextColor(Appearance.TEXT_COLOR_LABEL);
            }
            ViewUtil.setPadding(cardholderNameLabel, labelLeftPadding, null, null, null);
            cardholderNameLabel.setText(LocalizedStrings.getString(StringKey.ENTRY_CARDHOLDER_NAME));

            cardholderNameLayout
                    .addView(cardholderNameLabel, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            cardholderNameEdit = new EditText(this);
            cardholderNameEdit.setId(editTextIdCounter++);
            cardholderNameEdit.setMaxLines(1);
            cardholderNameEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
            cardholderNameEdit.setTextAppearance(getApplicationContext(),
                    android.R.attr.textAppearanceLarge);
            cardholderNameEdit.setInputType(InputType.TYPE_CLASS_TEXT);
            if(! useApplicationTheme ) {
                cardholderNameEdit.setHintTextColor(Appearance.TEXT_COLOR_EDIT_TEXT_HINT);
            }

            cardholderNameValidator = new MaxLengthValidator(MAX_CARDHOLDER_NAME_LENGTH);
            cardholderNameEdit.addTextChangedListener(cardholderNameValidator);
            cardholderNameEdit.addTextChangedListener(this);

            cardholderNameLayout.addView(cardholderNameEdit, LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);

            mainLayout.addView(cardholderNameLayout, LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
        } else {
            cardholderNameValidator = new AlwaysValid();
        }
    }
}
