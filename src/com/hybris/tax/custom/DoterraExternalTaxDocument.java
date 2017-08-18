/**
 *
 */
package com.hybris.tax.custom;

import de.hybris.platform.externaltax.ExternalTaxDocument;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author bibnayak
 *
 */
public class DoterraExternalTaxDocument extends ExternalTaxDocument {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	private Map<Integer, List<String>> doterraLineItemTaxes;
	private List<String> doterraShippingCostTaxes;
	private List<String> doterraDiscountCostTaxes;
	private String message;

	public Map<Integer, List<String>> getAllDoterraTaxes() {
		return getDoterraTaxesMap(false);
	}

	protected Map<Integer, List<String>> getDoterraTaxesMap(final boolean createIfAbsent) {
		if ((this.doterraLineItemTaxes == null) && (createIfAbsent)) {
			this.doterraLineItemTaxes = new HashMap();
		}
		return this.doterraLineItemTaxes == null ? Collections.EMPTY_MAP : this.doterraLineItemTaxes;
	}

	public List<String> getDoterraTaxesForOrderEntry(final int entryNumber) {
		final List<String> ret = getDoterraTaxesMap(false).get(Integer.valueOf(entryNumber));
		return ret == null ? Collections.EMPTY_LIST : ret;
	}

	/*
	 * public void setDoterraTaxesForOrderEntry(final int entryNumber, final
	 * List<TaxValue> taxes) { if (taxes == null) {
	 * getTaxesMap(true).remove(Integer.valueOf(entryNumber)); } else {
	 * getTaxesMap(true).put(Integer.valueOf(entryNumber), taxes); } }
	 */

	public void setTaxesForOrder(final int entryNumber, final List<String> taxForOrder) {
		if (taxForOrder == null) {
			getDoterraTaxesMap(true).remove(Integer.valueOf(entryNumber));
		} else {
			getDoterraTaxesMap(true).put(Integer.valueOf(entryNumber), taxForOrder);
		}
	}

	/*
	 * public void setDoterraTaxesForOrderEntry(final int entryNumber, final
	 * TaxValue... taxes) { setDoterraTaxesForOrderEntry(entryNumber, taxes ==
	 * null ? null : Arrays.asList(taxes)); }
	 */

	public void setMessage(final String message) {
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

	public List<String> getDoterraShippingCostTaxes() {
		return this.doterraShippingCostTaxes == null ? Collections.EMPTY_LIST : this.doterraShippingCostTaxes;
	}

	public void setDoterraShippingCostTaxes(final List<String> shippingCostTaxes) {
		this.doterraShippingCostTaxes = shippingCostTaxes;
	}

	public void setDoterraShippingCostTaxes(final String... shippingCostTaxes) {
		setDoterraShippingCostTaxes(shippingCostTaxes == null ? null : Arrays.asList(shippingCostTaxes));
	}

	public List<String> getDoterraDiscountCostTaxes() {
		return this.doterraDiscountCostTaxes == null ? Collections.EMPTY_LIST : this.doterraDiscountCostTaxes;
	}

	public void setDoterraDiscountCostTaxes(final List<String> discountCostTaxes) {
		this.doterraDiscountCostTaxes = discountCostTaxes;
	}

	public void setDoterraDiscountCostTaxes(final String... discountCostTaxes) {
		setDoterraDiscountCostTaxes(discountCostTaxes == null ? null : Arrays.asList(discountCostTaxes));
	}

}
