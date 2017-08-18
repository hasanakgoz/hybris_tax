/**
 *
 */
package com.hybris.tax.custom.calculation;

import de.hybris.platform.core.CoreAlgorithms;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.externaltax.ApplyExternalTaxesStrategy;
import de.hybris.platform.externaltax.ExternalTaxDocument;
import de.hybris.platform.util.TaxValue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.log4j.Logger;

import com.hybris.tax.custom.DoterraExternalTaxDocument;
import com.hybris.tax.custom.DoterraOrderHelper;


/**
 * @author bibnayak
 *
 */
public class DoterraApplyExternalTaxesStrategy implements ApplyExternalTaxesStrategy
{

	private static final String SALES_TAX = "st";

	private static final Logger LOG = Logger.getLogger(DoterraApplyExternalTaxesStrategy.class);


	@Resource(name = "doterraOrderHelper")
	private DoterraOrderHelper doterraOrderHelper;

	@Override
	public void applyExternalTaxes(final AbstractOrderModel order, final ExternalTaxDocument doterraExternalTaxes)
	{
		/*
		 * if (!Boolean.TRUE.equals(order.getNet())) { throw new IllegalStateException( "Order " + order.getCode() +
		 * " must be of type NET to apply external taxes to it."); }
		 */
		LOG.debug("Entered DoterraApplyExternalTaxesStrategy-applyExternalTaxes" + doterraExternalTaxes);
		try
		{

			BigDecimal entryTaxSum = new BigDecimal(0.0);
			BigDecimal discountTaxSum = new BigDecimal(0.0);
			// if(null!=order.getOrderType() &&
			// !order.getOrderType().equals(DoterraOrderTypeEnum.REPLACEMENT)){
			entryTaxSum = applyEntryTaxes(order, doterraExternalTaxes);
			discountTaxSum = applyDiscountCostTaxes(order, (DoterraExternalTaxDocument) doterraExternalTaxes);
			// }
			final BigDecimal shippingTaxSum = applyShippingCostTaxes(order, (DoterraExternalTaxDocument) doterraExternalTaxes);
			// add discount cost tax
			final BigDecimal totalTaxSum = entryTaxSum.add(shippingTaxSum).add(discountTaxSum);
			setTotalTax(order, totalTaxSum);
			LOG.debug("Leaving DoterraApplyExternalTaxesStrategy-applyExternalTaxes" + totalTaxSum);
		}
		catch (final Exception ex)
		{
			LOG.error("Exception in DoterraApplyExternalTaxesStrategy-applyExternalTaxes" + ex);

		}

	}

	protected BigDecimal applyEntryTaxes(final AbstractOrderModel order, final ExternalTaxDocument taxDoc)
	{
		LOG.debug("Entering DoterraApplyExternalTaxesStrategy-applyEntryTaxes" + taxDoc);
		BigDecimal totalTax = BigDecimal.ZERO;
		final DoterraExternalTaxDocument doterrataxDoc = (DoterraExternalTaxDocument) taxDoc;

		if (doterrataxDoc.getAllDoterraTaxes() == null)
		{
			LOG.error("Entering DoterraApplyExternalTaxesStrategy-getAllDoterraTaxes cannot be null");
		}
		final Set<Integer> consumedEntryNumbers = new HashSet(doterrataxDoc.getAllDoterraTaxes().keySet());
		for (final AbstractOrderEntryModel entry : order.getEntries())
		{
			final Integer entryNumber = entry.getEntryNumber();
			if (entryNumber == null)
			{
				throw new IllegalStateException("Order entry " + order.getCode() + "." + entry
						+ " does not have a entry number. Cannot apply external tax to it.");
			}
			final List<String> taxesForOrderEntry = doterrataxDoc.getDoterraTaxesForOrderEntry(entryNumber.intValue());
			if (taxesForOrderEntry != null)
			{
				for (final String taxForOrderEntry : taxesForOrderEntry)
				{

					// assertValidTaxValue(order, taxForOrderEntry);
					final Map<String, String> taxList = doterraOrderHelper.parseTaxValues(taxForOrderEntry);
					if (null != taxList)
					{
						LOG.debug("Entering DoterraApplyExternalTaxesStrategy-sales tax" + taxList);
						totalTax = totalTax.add(new BigDecimal(taxList.get(SALES_TAX)));
						LOG.debug("Leaving DoterraApplyExternalTaxesStrategy-sales tax" + totalTax);

					}
				}
			}
			// entry.setTaxValues(taxesForOrderEntry);
			entry.setDoterraTaxValues(taxesForOrderEntry);
			consumedEntryNumbers.remove(entryNumber);
		}
		if (!consumedEntryNumbers.isEmpty())
		{
			throw new IllegalArgumentException("External tax document " + doterrataxDoc
					+ " seems to contain taxes for more lines items than available within " + order.getCode());
		}
		order.setExlTotalTax(totalTax.doubleValue());
		LOG.debug("Leaving DoterraApplyExternalTaxesStrategy-applyEntryTaxes" + totalTax);

		return totalTax;
	}

	protected BigDecimal applyShippingCostTaxes(final AbstractOrderModel order, final DoterraExternalTaxDocument taxDoc)
	{
		LOG.debug("Entering DoterraApplyExternalTaxesStrategy-applyShippingCostTaxes" + taxDoc);

		BigDecimal totalTax = BigDecimal.ZERO;
		final List<String> shippingTaxes = taxDoc.getDoterraShippingCostTaxes();
		order.setDoterraOrderTaxValues(shippingTaxes);
		if (shippingTaxes != null)
		{
			for (final String taxForOrderEntry : shippingTaxes)
			{
				// assertValidTaxValue(order, taxForOrderEntry);

				totalTax = totalTax.add(new BigDecimal(doterraOrderHelper.parseTaxValues(taxForOrderEntry).get(SALES_TAX)));
			}
			order.setShippingTax(totalTax.doubleValue());
		}

		LOG.debug("Leaving DoterraApplyExternalTaxesStrategy-applyShippingCostTaxes" + totalTax);

		return totalTax;
	}

	/**
	 * @param order
	 * @param doterraExternalTaxes
	 * @return
	 */
	private BigDecimal applyDiscountCostTaxes(final AbstractOrderModel order,
			final DoterraExternalTaxDocument doterraExternalTaxes)
	{
		LOG.debug("Entering DoterraApplyExternalTaxesStrategy-applyDiscountCostTaxes" + doterraExternalTaxes);

		BigDecimal totalTax = BigDecimal.ZERO;
		final List<String> discountTaxes = doterraExternalTaxes.getDoterraDiscountCostTaxes();
		// order.setDoterraTaxValue(discountTaxes);
		if (discountTaxes != null)
		{
			for (final String taxForOrderEntry : discountTaxes)
			{
				totalTax = totalTax.add(new BigDecimal(doterraOrderHelper.parseTaxValues(taxForOrderEntry).get(SALES_TAX)));
			}
			order.setDiscountTax(totalTax.doubleValue());
		}
		LOG.debug("Leaving DoterraApplyExternalTaxesStrategy-applyDiscountCostTaxes" + totalTax);

		return totalTax;
	}

	protected void setTotalTax(final AbstractOrderModel order, final BigDecimal totalTaxSum)
	{
		LOG.debug("Entering DoterraApplyExternalTaxesStrategy-setTotalTax" + totalTaxSum);

		final Integer digits = order.getCurrency().getDigits();
		if (digits == null)
		{
			throw new IllegalStateException(
					"Order " + order.getCode() + " has got a currency without decimal digits defined. Cannot apply external taxes.");
		}
		order.setTotalTax(Double.valueOf(CoreAlgorithms.round(totalTaxSum.doubleValue(), digits.intValue())));
		order.setOrderTotalPrice(Double
				.valueOf(CoreAlgorithms.round(order.getTotalPrice().doubleValue() + totalTaxSum.doubleValue(), digits.intValue())));
		LOG.debug("Leaving DoterraApplyExternalTaxesStrategy-setTotalTax" + totalTaxSum);

	}

	protected void assertValidTaxValue(final AbstractOrderModel order, final TaxValue value)
	{
		if (!value.isAbsolute())
		{
			throw new IllegalArgumentException(
					"External tax " + value + " is not absolute. Cannot apply it to order " + order.getCode());
		}
		if (!order.getCurrency().getIsocode().equalsIgnoreCase(value.getCurrencyIsoCode()))
		{
			throw new IllegalArgumentException("External tax " + value + " currency " + value.getCurrencyIsoCode()
					+ " does not match order currency " + order.getCurrency().getIsocode() + ". Cannot apply.");
		}
	}

}
