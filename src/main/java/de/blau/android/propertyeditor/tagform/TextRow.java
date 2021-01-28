package de.blau.android.propertyeditor.tagform;

import java.util.List;
import java.util.Map;

import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.blau.android.App;
import de.blau.android.R;
import de.blau.android.names.Names;
import de.blau.android.names.Names.NameAndTags;
import de.blau.android.osm.Server;
import de.blau.android.osm.Tags;
import de.blau.android.prefs.Preferences;
import de.blau.android.presets.Preset.PresetItem;
import de.blau.android.presets.PresetComboField;
import de.blau.android.presets.PresetField;
import de.blau.android.presets.PresetTextField;
import de.blau.android.presets.ValueType;
import de.blau.android.presets.ValueWithCount;
import de.blau.android.propertyeditor.InputTypeUtil;
import de.blau.android.propertyeditor.SanitizeTextWatcher;
import de.blau.android.propertyeditor.TagEditorFragment;
import de.blau.android.propertyeditor.tagform.TagFormFragment.EditableLayout;
import de.blau.android.util.StringWithDescription;
import de.blau.android.views.CustomAutoCompleteTextView;

/**
 * An editable text only row for a tag with a dropdown containg suggestions
 * 
 * @author simon
 *
 */
public class TextRow extends LinearLayout implements KeyValueRow {

    protected static final String DEBUG_TAG = "TextRow";

    private TextView                   keyView;
    private CustomAutoCompleteTextView valueView;
    private ValueType                  valueType;

    /**
     * Construct a editable text row for a tag
     * 
     * @param context Android Context
     */
    public TextRow(Context context) {
        super(context);
    }

    /**
     * Construct a editable text row for a tag
     * 
     * @param context Android Context
     * @param attrs an AttributeSet
     */
    public TextRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (isInEditMode()) {
            return; // allow visual editor to work
        }
        setKeyView((TextView) findViewById(R.id.textKey));
        valueView = (CustomAutoCompleteTextView) findViewById(R.id.textValue);
    }

    /**
     * Set the text via id of the key view
     * 
     * @param k a string resource id for the key
     */
    public void setKeyText(int k) {
        getKeyView().setText(k);
    }

    /**
     * Set the ArrayAdapter for values
     * 
     * @param a the ArrayAdapter
     */
    public void setValueAdapter(ArrayAdapter<?> a) {
        valueView.setAdapter(a);
    }

    @Override
    public String getKey() {
        return (String) getKeyView().getTag();
    }

    @Override
    public String getValue() {
        return valueView.getText().toString();
    }

    /**
     * Get the current ValueType if any
     * 
     * @return the ValueType or null
     */
    @Nullable
    ValueType getValueType() {
        return valueType;
    }

    /**
     * Get the AutoCompleteTextView for the values
     * 
     * @return a CustomAutoCompleteTextView
     */
    public CustomAutoCompleteTextView getValueView() {
        return valueView;
    }

    /**
     * @return the keyView
     */
    public TextView getKeyView() {
        return keyView;
    }

    /**
     * @param keyView the keyView to set
     */
    public void setKeyView(TextView keyView) {
        this.keyView = keyView;
    }

    /**
     * Get a row for an unstructured text value
     * 
     * @param caller the calling TagFormFragment instance
     * @param inflater the inflater to use
     * @param rowLayout the Layout holding the row
     * @param preset the best matched PresetITem for the key
     * @param field the PresetField
     * @param value any existing value for the tag
     * @param values a list of all the predefined values in the PresetItem for the key
     * @param allTags a Map of the tags currently being edited
     * @return a TagTextRow instance
     */
    static TextRow getRow(@NonNull final TagFormFragment caller, @NonNull final LayoutInflater inflater, @NonNull final LinearLayout rowLayout,
            @Nullable final PresetItem preset, @NonNull final PresetField field, @Nullable final String value, @Nullable final List<String> values,
            final Map<String, String> allTags) {
        final TextRow row = (TextRow) inflater.inflate(R.layout.tag_form_text_row, rowLayout, false);
        final String key = field.getKey();
        final String hint = preset != null ? field.getHint() : null;
        final String defaultValue = field.getDefaultValue();
        row.valueType = preset != null ? preset.getValueType(key) : null;
        final boolean isMPHSpeed = Tags.isSpeedKey(key) && App.getGeoContext(rowLayout.getContext()).imperial(caller.propertyEditorListener.getElement());
        final TextView ourKeyView = row.getKeyView();
        ourKeyView.setText(hint != null ? hint : key);
        ourKeyView.setTag(key);
        final CustomAutoCompleteTextView ourValueView = row.getValueView();

        if (field instanceof PresetTextField) {
            final int length = ((PresetTextField) field).length();
            if (length > 0) { // if it isn't set don't bother
                ourValueView.getViewTreeObserver().addOnGlobalLayoutListener(new LayoutListener(ourKeyView, ourValueView, length));
            }
        }
        ourValueView.setText(value);

        // set empty value to be on the safe side
        ourValueView.setAdapter(new ArrayAdapter<>(caller.getActivity(), R.layout.autocomplete_row, new String[0]));

        if (field instanceof PresetComboField && ((PresetComboField) field).isMultiSelect() && preset != null) {
            // FIXME this should be somewhere better since it creates a non obvious side effect
            ourValueView.setTokenizer(new CustomAutoCompleteTextView.SingleCharTokenizer(preset.getDelimiter(key)));
        }
        setHint(field, ourValueView);
        ourValueView.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(DEBUG_TAG, "onFocusChange");
            String rowValue = row.getValue();
            if (!hasFocus && !rowValue.equals(value)) {
                caller.updateSingleValue(key, rowValue);
                if (rowLayout instanceof EditableLayout) {
                    ((EditableLayout) rowLayout).putTag(key, rowValue);
                }
            } else if (hasFocus) {
                ArrayAdapter<?> adapter = caller.getValueAutocompleteAdapter(key, values, preset, null, allTags);
                if (adapter != null && !adapter.isEmpty()) {
                    ourValueView.setAdapter(adapter);
                }
                if (isMPHSpeed) {
                    TagEditorFragment.initMPHSpeed(rowLayout.getContext(), ourValueView, caller.propertyEditorListener);
                } else if (row.getValueType() == null) {
                    InputTypeUtil.enableTextSuggestions(ourValueView);
                }
                InputTypeUtil.setInputTypeFromValueType(ourValueView, row.getValueType());
            }
        });
        ourValueView.setOnItemClickListener((parent, view, position, id) -> {
            Log.d(DEBUG_TAG, "onItemClicked value");
            Object o = parent.getItemAtPosition(position);
            if (o instanceof Names.NameAndTags) {
                ourValueView.setOrReplaceText(((NameAndTags) o).getName());
                caller.applyTagSuggestions(((NameAndTags) o).getTags(), caller::update);
                caller.update();
                return;
            } else {
                setOrReplaceText(ourValueView, o);
            }
            caller.updateSingleValue(key, row.getValue());
            if (rowLayout instanceof EditableLayout) {
                ((EditableLayout) rowLayout).putTag(key, row.getValue());
            }
        });
        ourValueView.addTextChangedListener(new SanitizeTextWatcher(caller.getActivity(), caller.maxStringLength));
        return row;
    }

    /**
     * Set the text to display
     * 
     * @param textView the CustomAutoCompleteTextView
     * @param text an Object holding the text
     */
    private static void setOrReplaceText(final CustomAutoCompleteTextView textView, Object text) {
        if (text instanceof ValueWithCount) {
            textView.setOrReplaceText(((ValueWithCount) text).getValue());
        } else if (text instanceof StringWithDescription) {
            textView.setOrReplaceText(((StringWithDescription) text).getValue());
        } else if (text instanceof String) {
            textView.setOrReplaceText((String) text);
        }
    }

    /**
     * Get a row for an unstructured text value for use outside of the property editor
     * 
     * @param context the calling TagFormFragment instance
     * @param inflater the inflater to use
     * @param rowLayout the Layout holding the row
     * @param preset the best matched PresetItem for the key
     * @param field the PresetField
     * @param value any existing value for the tag
     * @param adapter an optional ArrayAdapter with autocomplete values
     * @return a TagTextRow instance
     */
    public static TextRow getSimpleRow(@NonNull final Context context, @NonNull final LayoutInflater inflater, @NonNull final LinearLayout rowLayout,
            @Nullable final PresetItem preset, @NonNull final PresetField field, @Nullable final String value, @Nullable final ArrayAdapter<?> adapter) {
        final TextRow row = (TextRow) inflater.inflate(R.layout.tag_form_text_row, rowLayout, false);
        final String key = field.getKey();
        final String hint = preset != null ? field.getHint() : null;
        row.valueType = preset != null ? preset.getValueType(key) : null;
        final TextView ourKeyView = row.getKeyView();
        ourKeyView.setText(hint != null ? hint : key);
        ourKeyView.setTag(key);
        final CustomAutoCompleteTextView ourValueView = row.getValueView();

        if (field instanceof PresetTextField) {
            final int length = ((PresetTextField) field).length();
            if (length > 0) { // if it isn't set don't bother
                ourValueView.getViewTreeObserver().addOnGlobalLayoutListener(new LayoutListener(ourKeyView, ourValueView, length));
            }
        }
        ourValueView.setText(value);

        // set empty value to be on the safe side
        ourValueView.setAdapter(new ArrayAdapter<>(context, R.layout.autocomplete_row, new String[0]));

        setHint(field, ourValueView);
        ourValueView.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(DEBUG_TAG, "onFocusChange");
            String rowValue = row.getValue();
            if (!hasFocus && !rowValue.equals(value)) {
                if (rowLayout instanceof EditableLayout) {
                    ((EditableLayout) rowLayout).putTag(key, rowValue);
                }
            } else if (hasFocus) {
                if (adapter != null && !adapter.isEmpty()) {
                    ourValueView.setAdapter(adapter);
                }
                if (row.getValueType() == null) {
                    InputTypeUtil.enableTextSuggestions(ourValueView);
                }
                InputTypeUtil.setInputTypeFromValueType(ourValueView, row.getValueType());
            }
        });
        ourValueView.setOnItemClickListener((parent, view, position, id) -> {
            Log.d(DEBUG_TAG, "onItemClicked value");
            setOrReplaceText(ourValueView, parent.getItemAtPosition(position));
        });
        Server server = new Preferences(context).getServer();
        ourValueView.addTextChangedListener(new SanitizeTextWatcher(context, server.getCachedCapabilities().getMaxStringLength()));
        return row;
    }

    /**
     * Set the hint on the value view
     * 
     * @param field the PresetField for the tag
     * @param valueView the View for the value
     */
    private static void setHint(@Nullable final PresetField field, @NonNull final CustomAutoCompleteTextView valueView) {
        if (field instanceof PresetTextField) {
            valueView.setHint(R.string.tag_value_hint);
        } else {
            valueView.setHint(R.string.tag_autocomplete_value_hint);
        }
    }

    private static class LayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        final TextView                   ourKeyView;
        final CustomAutoCompleteTextView ourValueView;
        final int                        length;

        /**
         * Create a new listener
         * 
         * @param keyView the View holding the key
         * @param valueView the View holding the Value
         * @param length the length of the value view in characters
         */
        LayoutListener(@NonNull TextView keyView, @NonNull CustomAutoCompleteTextView valueView, int length) {
            this.ourKeyView = keyView;
            this.ourValueView = valueView;
            this.length = length;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onGlobalLayout() {
            ViewTreeObserver observer = ourValueView.getViewTreeObserver();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                observer.removeOnGlobalLayoutListener(this);
            } else {
                observer.removeGlobalOnLayoutListener(this); // NOSONAR
            }
            float aM = ourValueView.getPaint().measureText("M"); // FIXME cache this
            int lines = Math.min((int) (length / aM), 4);
            if (lines > 1) {
                ourValueView.setLines(lines);
                ourValueView.setMaxLines(lines);
                ourValueView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) ourValueView.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                ourValueView.setLayoutParams(layoutParams);
                ourValueView.setGravity(Gravity.TOP);
                layoutParams = (LinearLayout.LayoutParams) ourKeyView.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                ourKeyView.setLayoutParams(layoutParams);
                ourKeyView.setGravity(Gravity.TOP);
                ourValueView.requestLayout();
            }
        }
    }
}
