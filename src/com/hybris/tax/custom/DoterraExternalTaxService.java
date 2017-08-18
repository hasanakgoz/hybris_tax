/**
 *
 */
package com.hybris.tax.custom;

import de.hybris.platform.core.model.order.AbstractOrderModel;


/**
 * @author shganapa
 *
 */
public interface DoterraExternalTaxService
{

	/**
	 * Gets the price information of the product for the logged in customer
	 *
	 * @param product
	 * @param currencyIso
	 * @return DoterraPriceInformation with price informations
	 */

	public boolean calculateExternalTaxes(AbstractOrderModel abstractOrderModel);

}
