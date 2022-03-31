package de.blau.android.presets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xmlpull.v1.XmlSerializer;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import de.blau.android.R;
import de.blau.android.osm.OsmElement.ElementType;
import de.blau.android.views.WrappingLayout;

/**
 * Represents a preset group, which may contain items, groups and separators
 */
public class PresetGroup extends PresetElement {

    private static final String DEBUG_TAG = PresetGroup.class.getSimpleName();

    private boolean itemSort = true;

    /** Elements in this group */
    private List<PresetElement> elements = new ArrayList<>();

    /**
     * Construct a new PresetGroup
     * 
     * @param preset the Preset this belongs to
     * @param parent parent ParentGroup (or null if this is the root group)
     * @param name name of the element or null
     * @param iconpath the icon path (either "http://" URL or "presets/" local image reference) or null
     */
    public PresetGroup(@NonNull Preset preset, @Nullable PresetGroup parent, @Nullable String name, @Nullable String iconpath) {
        super(preset, parent, name, iconpath);
    }

    /**
     * Sets the flag for item sorting
     * 
     * @param sort if true PresetITems will be sorted
     */
    public void setItemSort(boolean sort) {
        itemSort = sort;
    }

    /**
     * Add a PresetElement to this group setting its parent to this
     * 
     * @param element the PresetElement to add
     */
    public void addElement(@NonNull PresetElement element) {
        addElement(element, true);
    }

    /**
     * Add a PresetElement to this group
     * 
     * @param element the PresetElement to add
     * @param setParent if true set the elements parent to this
     */
    public void addElement(@NonNull PresetElement element, boolean setParent) {
        elements.add(element);
        if (setParent) {
            element.setParent(this);
        }
    }

    /**
     * Remove a PresetELement from this group
     * 
     * @param element the PresetElement
     */
    public void removeElement(@NonNull PresetElement element) {
        elements.remove(element);
    }

    /**
     * Get the PresetElements in this group
     * 
     * @return a List of PresetElements
     */
    public List<PresetElement> getElements() {
        return elements;
    }

    /**
     * Returns a view showing this group's icon
     * 
     * @param handler the handler handling clicks on the icon
     * @param selected highlight the background if true
     * @return a view/button representing this PresetElement
     */
    @Override
    public View getView(@NonNull Context ctx, @Nullable final PresetClickHandler handler, boolean selected) {
        TextView v = super.getBaseView(ctx, selected);
        v.setTypeface(null, Typeface.BOLD);
        if (handler != null) {
            v.setOnClickListener(view -> handler.onGroupClick(PresetGroup.this));
            v.setOnLongClickListener(view -> handler.onGroupLongClick(PresetGroup.this));
        }
        v.setBackgroundColor(ContextCompat.getColor(ctx, selected ? R.color.material_deep_teal_200 : R.color.dark_grey));
        return v;
    }

    /**
     * Get a ScrollView for this PresetGroup
     * 
     * @param ctx Android Context
     * @param handler listeners for click events on the View, in null no listeners
     * @param type ElementType the views are applicable for, if null don't filter
     * @param selectedElement highlight the background if true, if null no selection
     * @param region region in question
     * @return a view showing the content (nodes, subgroups) of this group
     */
    @NonNull
    public View getGroupView(@NonNull Context ctx, @Nullable PresetClickHandler handler, @Nullable ElementType type, @Nullable PresetElement selectedElement,
            @Nullable String region) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scrollView.setSaveEnabled(false);
        return getGroupView(ctx, scrollView, handler, type, selectedElement, region);
    }

    /**
     * Add Views for all the PresetElements in this group to a ScrollView
     * 
     * @param ctx Android Context
     * @param scrollView the ScrollView to add the PresetElement Views to
     * @param handler listeners for click events on the View, in null no listeners
     * @param type ElementType the views are applicable for, if null don't filter
     * @param selectedElement highlight the background if true, if null no selection
     * @param country country in question
     * @return the supplied ScrollView
     */
    @NonNull
    public View getGroupView(@NonNull Context ctx, @NonNull ScrollView scrollView, @Nullable PresetClickHandler handler, @Nullable ElementType type,
            @Nullable PresetElement selectedElement, @Nullable String country) {
        scrollView.removeAllViews();
        WrappingLayout wrappingLayout = new WrappingLayout(ctx);
        wrappingLayout.setSaveEnabled(false);
        float density = ctx.getResources().getDisplayMetrics().density;
        // make transparent
        wrappingLayout.setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.transparent));
        wrappingLayout.setHorizontalSpacing((int) (Preset.SPACING * density));
        wrappingLayout.setVerticalSpacing((int) (Preset.SPACING * density));
        List<PresetElement> filteredElements = type == null ? elements : Preset.filterElements(elements, type);
        filteredElements = country == null ? filteredElements : filterElementsByCountry(filteredElements, country);
        if (itemSort) {
            List<PresetItem> tempItems = new ArrayList<>();
            List<PresetGroup> tempGroups = new ArrayList<>();
            List<PresetElement> tempElements = new ArrayList<>(filteredElements);
            filteredElements.clear();
            for (PresetElement element : tempElements) {
                if (element instanceof PresetItem) {
                    sortAndAddElements(filteredElements, tempGroups);
                    tempItems.add((PresetItem) element);
                } else if (element instanceof PresetGroup) {
                    sortAndAddElements(filteredElements, tempItems);
                    tempGroups.add((PresetGroup) element);
                } else { // PresetSeperator
                    sortAndAddElements(filteredElements, tempGroups);
                    sortAndAddElements(filteredElements, tempItems);
                    filteredElements.add(element);
                }
            }
            sortAndAddElements(filteredElements, tempGroups);
            sortAndAddElements(filteredElements, tempItems);
        }
        List<View> childViews = new ArrayList<>();
        for (PresetElement element : filteredElements) {
            View v = element.getView(ctx, handler, element.equals(selectedElement));
            if (v.getLayoutParams() == null) {
                Log.e(DEBUG_TAG, "layoutparams null " + element.getName());
            }
            childViews.add(v);
        }
        wrappingLayout.setWrappedChildren(childViews);
        scrollView.addView(wrappingLayout);
        return scrollView;
    }

    /**
     * Filter a List of PresetElement by country
     * 
     * @param elements the PresetElements
     * @param country the country
     * @return a List of PresetElement, potentially empty
     */
    @NonNull
    private List<PresetElement> filterElementsByCountry(List<PresetElement> elements, String country) {
        List<PresetElement> result = new ArrayList<>();
        for (PresetElement pe : elements) {
            if (pe.appliesIn(country)) {
                result.add(pe);
            }
        }
        return result;
    }

    /**
     * Sort the PresetElements in a temporary List and add them to a target List
     * 
     * @param <T> PresetElement sub-class
     * @param target target List
     * @param temp temp List
     */
    private <T extends PresetElement> void sortAndAddElements(@NonNull List<PresetElement> target, @NonNull List<T> temp) {
        if (!temp.isEmpty()) {
            Collections.sort(temp, (pe1, pe2) -> pe1.getTranslatedName().compareTo(pe2.getTranslatedName()));
            target.addAll(temp);
            temp.clear();
        }
    }

    @Override
    public void toXml(XmlSerializer s) throws IllegalArgumentException, IllegalStateException, IOException {
        s.startTag("", Preset.GROUP);
        s.attribute("", Preset.NAME, getName());
        String iconPath = getIconpath();
        if (iconPath != null) {
            s.attribute("", Preset.ICON, getIconpath());
        }
        for (PresetElement e : elements) {
            e.toXml(s);
        }
        s.endTag("", Preset.GROUP);
    }
}
