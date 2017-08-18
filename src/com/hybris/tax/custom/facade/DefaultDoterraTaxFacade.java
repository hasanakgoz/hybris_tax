package com.hybris.tax.custom.facade;

import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;

import javax.annotation.Resource;

import org.apache.log4j.Logger;

import com.hybris.tax.custom.decision.ExternalTaxesService;


/**
 * @author Shirisha
 *
 */
public class DefaultDoterraTaxFacade implements DoterraTaxFacade
{
	private static final Logger LOG = Logger.getLogger(DefaultDoterraTaxFacade.class);

	/*
	 * (non-Javadoc)
	 *
	 * @see com.hybris.doterra.facades.tax.DoterraTaxFacade#calculateTax()
	 */

	@Resource(name = "cartService")
	private CartService cartService;

	@Resource(name = "externalTaxesService")
	private ExternalTaxesService externalTaxesService;

	@Override
	public String calculateTax()
	{
		LOG.info("Logged in DefaultDoterraTaxFacade : calculateTax()");

		boolean isTaxCalculated = true;
		final CartModel cart = cartService.getSessionCart();

		try
		{
			// Doterra Tax Facade to calculate tax
			isTaxCalculated = externalTaxesService.calculateExternalTaxes(cart);
		}
		catch (final Exception ex)
		{
			LOG.error("Error in caclulateTax: ", ex);
		}
		if (isTaxCalculated)
		{
			LOG.info("Logged out DefaultDoterraTaxFacade : calculateTax()");
			return cart.getTotalTax() + "";
		}
		else
		{
			LOG.info("Logged out DefaultDoterraTaxFacade : calculateTax()");
			return "";
		}
	}

}
