/**
 *
 */
package com.hybris.tax.custom;

import de.hybris.platform.commerceservices.externaltax.CalculateExternalTaxesStrategy;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.externaltax.ExternalTaxDocument;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.log4j.Logger;

import com.hybris.doterra.core.model.DoterraProductModel;
import com.hybris.doterra.core.price.factory.DoterraPriceInformation;
import com.hybris.doterra.core.product.DoterraPriceService;
import com.hybris.doterra.core.service.DoterraCustomerAccountService;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationclient.DoterraTaxService;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataInvoiceType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataLineType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataTaxType;

/**
 * @author
 *
 */
public class DoterraExternalTaxesStrategy implements CalculateExternalTaxesStrategy {

	private static final Logger LOG = Logger.getLogger(DoterraExternalTaxesStrategy.class);

	private static final String RETAIL_PRICE_GROUP = "usretailusergroup";
	private static final String WHOLESALE_CUSTOMER_PRICE_GROUP = "uswsusergroup";

	@Resource(name = "doterraTaxService")
	private DoterraTaxService doterraTaxService;

	@Resource(name = "priceService")
	private DoterraPriceService priceService;

	@Resource(name = "doterraCustomerAccountService")
	private DoterraCustomerAccountService doterraCustomerAccountService;

	/**
	 * Gets the tax information of the order for the logged in customer
	 *
	 * @param abstractOrder
	 * @return ExternalTaxDocument with tax informations
	 */

	@Override
	public ExternalTaxDocument calculateExternalTaxes(final AbstractOrderModel abstractOrder) {
		LOG.debug("Entered DoterraExternalTaxesStrategy" + abstractOrder);
		final DoterraExternalTaxDocument externalTaxDocument = new DoterraExternalTaxDocument();
		if (abstractOrder == null) {
			throw new IllegalStateException("Order is null. Cannot apply external tax to it.");
		} else {
			try {
				// Getting customer account type
				final UserGroupModel userGroup = doterraCustomerAccountService
						.getUserGroupForCustomer(abstractOrder.getUser());

				// Retrieving retail & wholesale price for entries
				final Map<Integer, Map<String, String>> priceDetails = new HashMap<Integer, Map<String, String>>();
				Map<String, String> priceValues = null;
				AbstractOrderModel cart;
				if (abstractOrder instanceof CartModel) {
					cart = abstractOrder;
				} else {
					cart = abstractOrder;
				}
				Map<Integer,AbstractOrderEntryModel> entryNoList = new HashMap<Integer,AbstractOrderEntryModel>();
				for (final AbstractOrderEntryModel e : cart.getEntries()) {
					priceValues = new HashMap<String, String>();
					boolean isRetailPrice = true;
					final DoterraProductModel productModel = (DoterraProductModel) e.getProduct();
					final DoterraPriceInformation retailPriceInfo = priceService.getPriceInformationForUPG(productModel,
							RETAIL_PRICE_GROUP, cart.getCurrency().getIsocode());
					if (retailPriceInfo != null) {
						priceValues.put("retailPrice", String.valueOf(retailPriceInfo.getSrc().getPrice()));
					} else {
						isRetailPrice = false;
					}

					final DoterraPriceInformation wholsalePriceInfo = priceService.getPriceInformationForUPG(
							productModel, WHOLESALE_CUSTOMER_PRICE_GROUP, cart.getCurrency().getIsocode());
					if (wholsalePriceInfo != null) {
						priceValues.put("wholesalePrice", String.valueOf(wholsalePriceInfo.getSrc().getPrice()));
					} else {
						priceValues.put("wholesalePrice", String.valueOf(retailPriceInfo.getSrc().getPrice()));
					}

					if (!isRetailPrice) {
						priceValues.put("retailPrice", String.valueOf(wholsalePriceInfo.getSrc().getPrice()));
					}
					priceDetails.put(e.getEntryNumber(), priceValues);
					entryNoList.put(e.getEntryNumber(),e);

				}
				LOG.debug("DoterraExternalTaxesStrategy Price details calculated" + priceDetails);

				final OutdataInvoiceType invoiceData = doterraTaxService.taxCalculation(cart, priceDetails, userGroup);
				if (null != invoiceData && null != invoiceData.getLINE()) {
					final List<OutdataLineType> lineItems = invoiceData.getLINE();
					for (final OutdataLineType taxLine : lineItems) {
						final List<String> tax = new ArrayList<String>();
						final List<String> shippingTax = new ArrayList<String>();
						final List<String> discountTax = new ArrayList<String>();
						String discountedTotalTax = null;// NOPMD
						if (null != taxLine) {
							for (final OutdataTaxType taxData : taxLine.getTAX()) {

								if (null != taxData) {

									if (null != entryNoList.get(Integer.parseInt(taxLine.getID())-1)) {
										final String taxValue = populateTaxDocument(taxData,
												entryNoList.get(Integer.parseInt(taxLine.getID()) - 1), null);
										LOG.debug("DoterraExternalTaxesStrategy line entry---->"
												+ entryNoList.get(Integer.parseInt(taxLine.getID()) - 1)
												+ "Line Tax values------:" + taxValue);

										tax.add(taxValue);
									} else {

										if (taxLine.getGROSSAMOUNT() != null
												&& taxLine.getGROSSAMOUNT().contains("-")) {

											final String discountTaxValue = populateTaxDocument(taxData, null,
													"Discount");

											discountTax.add(discountTaxValue);
											discountedTotalTax = taxLine.getTOTALTAXAMOUNT();
											LOG.debug("DoterraExternalTaxesStrategy discount tax" + discountTaxValue);

										} else {

											final String shippingTaxValue = populateTaxDocument(taxData, null,
													"Shipping");
											shippingTax.add(shippingTaxValue);
											LOG.debug("DoterraExternalTaxesStrategy shipping tax" + shippingTax);

										}
									}
								}
							}
						}
						if (null != discountTax && discountTax.size() > 0) {
							externalTaxDocument.setDoterraDiscountCostTaxes(discountTax);
						}
						if (null != shippingTax && shippingTax.size() > 0) {
							externalTaxDocument.setDoterraShippingCostTaxes(shippingTax);
						}
						if (null != tax && tax.size() > 0) {
							LOG.debug("DoterraExternalTaxesStrategy line entry list size---" + tax.size());
							externalTaxDocument.setTaxesForOrder(Integer.parseInt(taxLine.getID()) - 1, tax);
						}
						LOG.debug("DoterraExternalTaxesStrategy Tax calculaton is done" + externalTaxDocument);

					}

				} else {
					return null;
				}

			} catch (final Exception ex) {
				LOG.error("Exception from Sabrix Tax Service", ex);
				if (ex.getMessage().contains("TaxException")) {
					externalTaxDocument.setMessage("TaxException");
				} else {
					return null;
				}
			}

			return externalTaxDocument;
		}
	}

	/**
	 * @return the doterraTaxService
	 */
	public DoterraTaxService getDoterraTaxService() {
		return doterraTaxService;
	}

	/**
	 * @param doterraTaxService
	 *            the doterraTaxService to set
	 */
	public void setDoterraTaxService(final DoterraTaxService doterraTaxService) {
		this.doterraTaxService = doterraTaxService;
	}

	private String populateTaxDocument(final OutdataTaxType taxData, final AbstractOrderEntryModel entry,
			final String line) {
		final DecimalFormat df = new DecimalFormat("#.##");
		// discountTaxValue.setTaxableTotal(Double.parseDouble(taxData.getTAXABLEBASIS().getDOCUMENTAMOUNT()));
		// discountTaxValue.setSalesTax(new
		// Double(df.format(Double.parseDouble(taxData.getTAXAMOUNT().getDOCUMENTAMOUNT()))));
		// discountTaxValue.setTaxPercent(new
		// Double(df.format(Double.parseDouble(taxData.getTAXRATE())))*100);
		// discountTaxValue.setExemptionAmount(Double.parseDouble(taxData.getEXEMPTAMOUNT().getDOCUMENTAMOUNT()));
		// // discountTaxValue.setProductCode(cart.getCode());
		// discountTaxValue.setJurisdiction(taxData.getZONENAME());// TODO
		// if(null != entry){
		// discountTaxValue.setTaxCode(entry.getProduct().getCode());
		// }else{
		// discountTaxValue.setTaxCode(line);
		// }
		final StringBuilder taxValue = new StringBuilder();
		try {
			final NumberFormat df2 = NumberFormat.getInstance();
			df2.setMinimumFractionDigits(2);
			df2.setRoundingMode(RoundingMode.DOWN);
			taxValue.append("tt:" + df2.format(Double.parseDouble(taxData.getTAXABLEBASIS().getDOCUMENTAMOUNT())));
			taxValue.append(
					";st:" + new Double(df.format(Double.parseDouble(taxData.getTAXAMOUNT().getDOCUMENTAMOUNT()))));
			taxValue.append(";ea:" + df2.format(Double.parseDouble(taxData.getEXEMPTAMOUNT().getDOCUMENTAMOUNT())));
			taxValue.append(";jt:" + taxData.getZONENAME());
			if (null != entry) {
				taxValue.append(";tc:" + entry.getProduct().getCode());
			} else {
				taxValue.append(";tc:" + line);
			}

			final NumberFormat df1 = NumberFormat.getInstance();
			df1.setMinimumFractionDigits(3);
			df1.setRoundingMode(RoundingMode.DOWN);
			taxValue.append(";tp:" + df1.format(Double.parseDouble(taxData.getTAXRATE()) * 100));
		} catch (final Exception ex) {
			LOG.error("Exception in populateTaxDocument" + ex);

		}
		// taxValues.add(taxes.toString());

		return taxValue.toString();
	}

}
