/**
 *
 */
package com.hybris.tax.custom;

import de.hybris.platform.commerceservices.externaltax.RecalculateExternalTaxesStrategy;
import de.hybris.platform.commerceservices.externaltax.impl.DefaultExternalTaxesService;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.order.price.TaxModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.cronjob.model.CronJobModel;
import de.hybris.platform.jalo.SessionContext;
import de.hybris.platform.properties.model.DoterraPropertiesModel;
import de.hybris.platform.servicelayer.cronjob.CronJobService;
import de.hybris.platform.servicelayer.event.EventService;
import de.hybris.platform.servicelayer.keygenerator.KeyGenerator;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionExecutionBody;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.util.DiscountValue;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;

import org.apache.log4j.Logger;

import com.hybris.doterra.core.dao.DoterraTaxDetailsDao;
import com.hybris.doterra.core.enums.DoterraOrderTypeEnum;
import com.hybris.doterra.core.event.DoterraMailServiceEvent;
import com.hybris.doterra.core.service.DoterraConfigurationPropertiesService;

/**
 * @author shganapa
 *
 */
public class DoterraExternalTaxServiceImpl extends DefaultExternalTaxesService {

	private static final Logger LOG = Logger.getLogger(DoterraExternalTaxServiceImpl.class);

	public static final String SESSION_EXTERNAL_TAX_DOCUMENT = "externalTaxDocument";
	public static final String TAX_FALLBACK_ZEROTAXRATE_STATES = "doterra.tax.fallback.zeroTaxRateStates";
	protected static final String DEFAULT_SITE_ID = "US";

	private RecalculateExternalTaxesStrategy recalculateExternalTaxesStrategy;

	@Resource(name = "doterraTaxDetailsDao")
	private DoterraTaxDetailsDao doterraTaxDetailsDao;

	@Resource(name = "doterraConfigurationPropertiesService")
	private DoterraConfigurationPropertiesService doterraConfigurationPropertiesService;

	@Resource(name = "modelService")
	private ModelService modelService;

	@Resource(name = "cronJobService")
	private CronJobService cronJobService;

	@Resource(name = "eventService")
	private EventService eventService;

	private KeyGenerator keyGenerator;

	@Resource(name = "sessionService")
	private SessionService sessionService;

	@Resource(name = "baseSiteService")
	private BaseSiteService baseSiteService;

	@Override
	public boolean calculateExternalTaxes(final AbstractOrderModel abstractOrder) {

		LOG.debug("Entered DoterraExternalTaxServiceImpl");
		if (getDecideExternalTaxesStrategy().shouldCalculateExternalTaxes(abstractOrder)) {
			try {

				if (abstractOrder.getInvoice() != null) {
					if (abstractOrder.getInvoice().equals("")) {
						abstractOrder.setInvoice(String.valueOf(keyGenerator.generate()));
						// saveOrder(abstractOrder);
					}
				} else {
					abstractOrder.setInvoice(String.valueOf(keyGenerator.generate()));
					// saveOrder(abstractOrder);
				}

				// final DoterraPropertiesModel property =
				// doterraConfigurationPropertiesService.getPropertyModelForKey("tax.fallback.mode");
				// if
				// (getRecalculateExternalTaxesStrategy().recalculate(abstractOrder))
				// {
				abstractOrder.setTaxFallbackMode("H");
				// saveOrder(abstractOrder);

				final DoterraExternalTaxDocument exTaxDocument = (DoterraExternalTaxDocument) sessionService
						.executeInLocalView(new SessionExecutionBody() {
							@Override
							public Object execute() {
								sessionService.setAttribute(SessionContext.USER, abstractOrder.getUser());
								if (baseSiteService.getCurrentBaseSite() == null) {
									baseSiteService.setCurrentBaseSite(
											baseSiteService.getBaseSiteForUID(DEFAULT_SITE_ID), true);
								}
								return (DoterraExternalTaxDocument) getCalculateExternalTaxesStrategy().calculateExternalTaxes(abstractOrder);
							}
						});
				/*
				 * final DoterraExternalTaxDocument exTaxDocument =
				 * (DoterraExternalTaxDocument)
				 * getCalculateExternalTaxesStrategy()
				 * .calculateExternalTaxes(abstractOrder);
				 */

				// Assert.notNull(exTaxDocument, "ExternalTaxDocument should not
				// be null");
				// check if external tax calculation was successful
				if (null != exTaxDocument && null != exTaxDocument.getMessage()
						&& exTaxDocument.getMessage().equalsIgnoreCase("TaxException")) {
					LOG.debug("Entered DoterraExternalTaxServiceImpl-TaxException");
					applyFallbackTaxes(abstractOrder);
					saveOrder(abstractOrder);
					checkLRPFailureStatus(abstractOrder);
				} else if (null != exTaxDocument && ((null != exTaxDocument.getAllDoterraTaxes()
						&& exTaxDocument.getAllDoterraTaxes().size() > 0)
						|| null != exTaxDocument.getDoterraShippingCostTaxes()
						|| null != exTaxDocument.getDoterraDiscountCostTaxes())) {
					LOG.debug("Entered DoterraExternalTaxServiceImpl-Received ExternalTaxDoc");
					getApplyExternalTaxesStrategy().applyExternalTaxes(abstractOrder, exTaxDocument);
					getSessionService().setAttribute(SESSION_EXTERNAL_TAX_DOCUMENT, exTaxDocument);
					// abstractOrder.setOrderTotalPrice(abstractOrder.getTotalPrice()+abstractOrder.getTotalTax());
					// getModelService().saveAll(abstractOrder.getDoterraTaxValue());
					saveOrder(abstractOrder);
					checkLRPSuccessStatus(abstractOrder);
					return true;
				} else {
					LOG.debug("Entered DoterraExternalTaxServiceImpl-Not Received ExternalTaxDoc");
					// the external tax calculation failed
					getSessionService()
							.removeAttribute(recalculateExternalTaxesStrategy.SESSION_ATTIR_ORDER_RECALCULATION_HASH);
					clearSessionTaxDocument();
					// clearTaxValues(abstractOrder); --> As this method is
					// called in applyFallbackTaxes() method
					applyFallbackTaxes(abstractOrder);
					saveOrder(abstractOrder);
					return true;
				}
				/*
				 * } else { // Tax is already saved to order so retrieve from
				 * there DoterraExternalTaxDocument taxDoc =
				 * getSessionService().getAttribute(
				 * SESSION_EXTERNAL_TAX_DOCUMENT);
				 * getApplyExternalTaxesStrategy().applyExternalTaxes(
				 * abstractOrder, taxDoc); saveOrder(abstractOrder); return
				 * true; }
				 */
			} catch (final Exception ex) {
				LOG.error("External Tax calculation failed with reason " + ex);
				applyFallbackTaxes(abstractOrder);
				saveOrder(abstractOrder);
				checkLRPFailureStatus(abstractOrder);
				return true;

			}
		}
		return false;
	}

	@Override
	protected void saveOrder(final AbstractOrderModel abstractOrder) {
		// getModelService().save(abstractOrder);
		// getModelService().saveAll(abstractOrder.getEntries());
		setCalculatedStatus(abstractOrder);
	}

	public boolean applyFallbackTaxes(final AbstractOrderModel abstractOrder) {
		LOG.debug("Entered DoterraExternalTaxServiceImpl-applyFallbackTaxes");
		clearTaxValues(abstractOrder);
		String zipCode = null;
		String geoCode = null;
		double taxRate = 0.09;

		try {
			final AddressModel address = abstractOrder.getDeliveryAddress();
			if (null != address) {
				final String postalCode = address.getPostalcode();
				if (postalCode.contains("-")) {
					final String[] zipCodes = postalCode.split("-");
					zipCode = zipCodes[0];
					geoCode = zipCodes[1];
				} else {
					zipCode = postalCode;
				}
			}

			List<Double> geoCodeTaxRate = null;
			List<Double> zipCodeTaxRate = null;

			final String zeroTaxRateStates = doterraConfigurationPropertiesService
					.getPropertyForKey(TAX_FALLBACK_ZEROTAXRATE_STATES);

			if (null != zipCode) {
				final List<TaxModel> taxData = doterraTaxDetailsDao.getTaxes(zipCode);
				if (null != taxData) {
					zipCodeTaxRate = new ArrayList<Double>();
					for (final TaxModel tax : taxData) {
						if (null != tax.getZipCode() && (zipCode.equalsIgnoreCase(tax.getZipCode().trim()))) {
							int min = 0;
							int max = 0;
							if (null != geoCode) {
								if (null != tax.getGeoCode()) {
									final String code = tax.getGeoCode();
									if (code.trim().contains("-")) {
										final String[] splitCode = code.split("-");
										min = Integer.parseInt(splitCode[0]);
										max = Integer.parseInt(splitCode[1]);
										if (Integer.parseInt(geoCode.trim()) <= max
												&& Integer.parseInt(geoCode.trim()) >= min) {
											geoCodeTaxRate = new ArrayList<Double>();
											geoCodeTaxRate.add(Double.parseDouble(tax.getRate()));
											break;
										} else {
											zipCodeTaxRate.add(Double.parseDouble(tax.getRate()));
										}
									}
								} else {
									zipCodeTaxRate.add(Double.parseDouble(tax.getRate()));
								}

							} else {
								zipCodeTaxRate.add(Double.parseDouble(tax.getRate()));
							}
						}

					}
				} else {
					LOG.error("No tax rate availaible");
				}
			} else {
				LOG.error("Zip code is not availaible");
			}
			if (null != geoCodeTaxRate && geoCodeTaxRate.size() > 0) {
				taxRate = geoCodeTaxRate.get(0);
			} else if (null != zipCodeTaxRate && zipCodeTaxRate.size() > 0) {
				taxRate = Collections.max(zipCodeTaxRate);
			} else if (!zeroTaxRateStates.isEmpty() && null != address.getRegion()) {
				final List<String> myTaxList = new ArrayList<String>(Arrays.asList(zeroTaxRateStates.split(",")));
				if (myTaxList.contains(address.getRegion().getIsocodeShort())) {
					taxRate = 0.00;
				}
			}
			LOG.debug("Entered DoterraExternalTaxServiceImpl-Tax rate" + taxRate);

		} catch (final Exception ex) {
			LOG.error("Exception during tax fallback strtaegy" + ex.getMessage());
		}

		final NumberFormat df1 = NumberFormat.getInstance();
		df1.setMinimumFractionDigits(3);
		df1.setRoundingMode(RoundingMode.DOWN);
		final Double totalPrice = abstractOrder.getTotalPrice();
		if (null != totalPrice) {
			final double totalTax = totalPrice * taxRate;
			final DecimalFormat df = new DecimalFormat("#.##");
			double entryPrices = 0.0;
			if (null != abstractOrder.getOrderType() && !(abstractOrder.getOrderType().equals(DoterraOrderTypeEnum.FREE)
					|| abstractOrder.getOrderType().equals(DoterraOrderTypeEnum.REPLACEMENT))) {
				for (final AbstractOrderEntryModel entry : abstractOrder.getEntries()) {
					final StringBuilder taxValue = new StringBuilder();
					taxValue.append("tc:" + entry.getProduct().getCode());
					taxValue.append(";tt:" + entry.getTotalPrice());
					entryPrices = entryPrices + Double.valueOf(df.format(entry.getTotalPrice() * taxRate));
					taxValue.append(";st:" + Double.valueOf(df.format(entry.getTotalPrice() * taxRate)));// salesTax
					taxValue.append(";tp:" + df1.format(taxRate * 100));// setTaxPercent
					final List<String> taxValues = new ArrayList<String>();
					taxValues.add(taxValue.toString());
					entry.setDoterraTaxValues(taxValues);
				}
			}
			double shippingDiscount = 0.0;

			final List<DiscountValue> globalDiscounts = abstractOrder.getGlobalDiscountValues();
			double otherDiscounts = 0.0;
			if (null != abstractOrder.getTotalDiscounts()) {
				otherDiscounts = abstractOrder.getTotalDiscounts().doubleValue();
			}

			if (null != globalDiscounts && globalDiscounts.size() > 0) {
				for (final DiscountValue discount : globalDiscounts) {
					if (null != discount.getCode() && discount.getCode().contains(("Shipping"))) {
						shippingDiscount = shippingDiscount + discount.getAppliedValue();
					}
				}
			}
			if (shippingDiscount > 0.0) {
				otherDiscounts = otherDiscounts - shippingDiscount;
			}
			StringBuilder taxValue = null;
			final List<String> taxValues = new ArrayList<String>();
			if (null != abstractOrder.getDeliveryCost() && abstractOrder.getDeliveryCost().doubleValue() != 0.0) {
				abstractOrder.setShippingTax(
						Double.valueOf(df.format((abstractOrder.getDeliveryCost() - shippingDiscount) * taxRate)));
				taxValue = new StringBuilder();
				taxValue.append("tc:" + "Shipping");
				taxValue.append(";tt:" + abstractOrder.getDeliveryCost().doubleValue());
				taxValue.append(";st:" + abstractOrder.getShippingTax());//
				taxValue.append(";tp:" + df1.format(taxRate * 100));// tax
																	// percent
				taxValues.add(taxValue.toString());
				abstractOrder.setDoterraOrderTaxValues(taxValues);
			}
			abstractOrder.setExlTotalTax(Double.valueOf(df.format(entryPrices)));
			abstractOrder.setDiscountTax(Double.valueOf(df.format(otherDiscounts * taxRate)));
			if (null != abstractOrder.getOrderType() && (abstractOrder.getOrderType().equals(DoterraOrderTypeEnum.FREE)
					|| abstractOrder.getOrderType().equals(DoterraOrderTypeEnum.REPLACEMENT))) {
				abstractOrder.setTotalTax(Double.valueOf(df.format(totalTax)));
			} else {
				abstractOrder.setTotalTax(Double.valueOf(df.format(abstractOrder.getExlTotalTax()
						+ abstractOrder.getShippingTax() - abstractOrder.getDiscountTax())));
			}
			abstractOrder.setOrderTotalPrice(Double.valueOf(df.format(totalTax + totalPrice.doubleValue())));
			abstractOrder.setTaxFallbackMode("F");
			// getModelService().saveAll(abstractOrder.getDoterraTaxValue());
			// saveOrder(abstractOrder);
			LOG.debug("Entered DoterraExternalTaxServiceImpl-Tax calculation on fallback mode is successful");
			return true;
		}

		return false;
	}

	@Override
	protected void clearTaxValues(final AbstractOrderModel abstractOrder) {
		abstractOrder.setTotalTaxValues(Collections.EMPTY_LIST);
		abstractOrder.setTotalTax(0.0);
		abstractOrder.setExlTotalTax(0.0);
		abstractOrder.setDiscountTax(0.0);
		abstractOrder.setShippingTax(0.0);
		abstractOrder.setOrderTotalPrice(abstractOrder.getTotalPrice());
		abstractOrder.setDoterraTaxValue(Collections.EMPTY_LIST);

		for (final AbstractOrderEntryModel entryModel : abstractOrder.getEntries()) {
			entryModel.setTaxValues(Collections.EMPTY_LIST);
			entryModel.setDoterraTaxValue(Collections.EMPTY_LIST);
		}

		// saveOrder(abstractOrder);
	}

	protected void checkLRPFailureStatus(final AbstractOrderModel abstractOrder) {
		try {
			LOG.info("Entered checkLRPFailureStatus-getFromCronJob-------" + abstractOrder.getFromCronJob());
			if (null != abstractOrder.getOrderType() && abstractOrder.getOrderType().equals(DoterraOrderTypeEnum.LRP)
					&& null != abstractOrder.getFromCronJob() && abstractOrder.getFromCronJob()) {

				abstractOrder.setFromCronJob(false);
				final String failThreshold = doterraConfigurationPropertiesService
						.getPropertyForKey("tax.fallback.lrpfailthreshold");
				final DoterraPropertiesModel failSequence = doterraConfigurationPropertiesService
						.getPropertyModelForKey("tax.fallback.consecutiveLrpFail");
				final DoterraPropertiesModel failCount = doterraConfigurationPropertiesService
						.getPropertyModelForKey("tax.fallback.lrpfailcount");
				final CronJobModel cronJob = cronJobService.getCronJob("doterraLrpTemplatesProcessCronJob");

				if (null == failSequence || null == failCount || null == failThreshold) {
					LOG.error("Failed retrieving tax constants");
					return;
				}

				if (null != failThreshold && null != failCount.getValues() && null != failSequence.getValues()) {
					final int count = Integer.parseInt(failCount.getValues()) + 1;
					if (failSequence.getValues().equalsIgnoreCase("false") && !cronJob.getJob().getRequestAbort()) {
						failSequence.setName("tax.fallback.consecutiveLrpFail");
						failSequence.setValues("true");
						// modelService.save(failSequence);
					}
					if (count <= Integer.parseInt(failThreshold) && failSequence.getValues().equalsIgnoreCase("true")
							&& !cronJob.getJob().getRequestAbort()) {
						failCount.setName("tax.fallback.lrpfailcount");
						failCount.setValues(String.valueOf(count));
						// modelService.save(failCount);
					} else if (count > Integer.parseInt(failThreshold)
							&& failSequence.getValues().equalsIgnoreCase("true")) {
						// CronJobModel cronJob =
						// (CronJobModel)cronJobService.getCronJob("doterraLrpTemplateProcessCronJob");
						failSequence.setName("tax.fallback.consecutiveLrpFail");
						failSequence.setValues("false");
						// modelService.save(failSequence);
						failCount.setName("tax.fallback.lrpfailcount");
						failCount.setValues(0 + "");
						// modelService.save(failCount);
						// cronJob.setStatus(CronJobStatus.ABORTED);
						// cronJob.setResult(CronJobResult.ERROR);
						cronJob.getJob().setRequestAbort(true);
						cronJob.getJob().setActive(false);
						// modelService.save(cronJob);
						cronJobService.requestAbortCronJob(cronJob);
						final StringBuffer message = new StringBuffer();
						message.append("Hi Team\n");
						message.append("LRP cronjob stopped due to consecutive failure of OITD service\n");
						eventService.publishEvent(new DoterraMailServiceEvent("TaxMailService", null, message,
								"LRP cron job stopped due to OITD service failure"));
					}
					modelService.saveAll(failCount, failSequence, cronJob);
					LOG.debug("checkLRPFailureStatus-failSequence.getValue()-------" + failSequence.getValues());
					LOG.debug("checkLRPFailureStatus-failCount.getValue()-------" + failCount.getValues());
					LOG.debug("checkLRPFailureStatus-getFromCronJob-------" + abstractOrder.getFromCronJob());

				} else {
					LOG.error("Failed retrieving tax constants");
					return;
				}
			}
		} catch (final Exception ex) {
			LOG.error("Exception in checkLRPFailureStatus" + ex);
		}
	}

	protected void checkLRPSuccessStatus(final AbstractOrderModel abstractOrder) {
		try {
			LOG.info("Entered checkLRPSuccessStatus-getFromCronJob-------" + abstractOrder.getFromCronJob());
			if (null != abstractOrder.getOrderType() && abstractOrder.getOrderType().equals(DoterraOrderTypeEnum.LRP)
					&& null != abstractOrder.getFromCronJob() && abstractOrder.getFromCronJob()) {
				abstractOrder.setFromCronJob(false);
				final String failThreshold = doterraConfigurationPropertiesService
						.getPropertyForKey("tax.fallback.lrpfailthreshold");
				final DoterraPropertiesModel failSequence = doterraConfigurationPropertiesService
						.getPropertyModelForKey("tax.fallback.consecutiveLrpFail");
				final DoterraPropertiesModel failCount = doterraConfigurationPropertiesService
						.getPropertyModelForKey("tax.fallback.lrpfailcount");
				if (null == failSequence || null == failCount || null == failThreshold) {
					LOG.error("Failed retrieving tax constants");
					return;
				}

				if (null != failThreshold && null != failCount.getValues() && null != failSequence.getValues()) {
					final int count = Integer.parseInt(failCount.getValues());
					if (count <= Integer.parseInt(failThreshold) && failSequence.getValues().equalsIgnoreCase("true")) {
						failSequence.setName("tax.fallback.consecutiveLrpFail");
						failSequence.setValues("false");
						modelService.save(failSequence);
						failCount.setName("tax.fallback.lrpfailcount");
						failCount.setValues(0 + "");
						modelService.save(failCount);
					}

					LOG.debug("checkLRPSuccessStatus-failSequence.getValue()-------" + failSequence.getValues());
					LOG.debug("checkLRPSuccessStatus-failCount.getValue()-------" + failCount.getValues());
					LOG.debug("checkLRPSuccessStatus-getFromCronJob-------" + abstractOrder.getFromCronJob());
				} else {
					LOG.error("Failed retrieving tax constants");
					return;
				}
			}
		} catch (final Exception ex) {
			LOG.error("Exception in checkLRPFailureStatus" + ex);

		}

	}

	/**
	 * @return the keyGenerator
	 */
	public KeyGenerator getKeyGenerator() {
		return keyGenerator;
	}

	/**
	 * @param keyGenerator
	 *            the keyGenerator to set
	 */
	public void setKeyGenerator(final KeyGenerator keyGenerator) {
		this.keyGenerator = keyGenerator;
	}

}
