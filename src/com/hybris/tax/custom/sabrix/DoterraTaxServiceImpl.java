/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2016 SAP SE
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * Hybris ("Confidential Information"). You shall not disclose such
 * Confidential Information and shall use it only in accordance with the
 * terms of the license agreement you entered into with SAP Hybris.
 */
package com.hybris.tax.custom.sabrix;

import de.hybris.platform.core.GenericSearchConstants.LOG;
import de.hybris.platform.core.model.c2l.CountryModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.integration.commons.hystrix.HystrixExecutable;
import de.hybris.platform.integration.commons.hystrix.OndemandHystrixCommandConfiguration;
import de.hybris.platform.integration.commons.hystrix.OndemandHystrixCommandFactory;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.util.Config;
import de.hybris.platform.util.DiscountValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.xml.namespace.QName;
import javax.xml.ws.Binding;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Component;

import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.IndataInvoiceType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.IndataLineType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.IndataType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataErrorType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataInvoiceType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataRequestStatusType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.QuantitiesType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.QuantityType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.RegistrationsType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationFault_Exception;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationRequest;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationResponse;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationService;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.UserElementType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.VersionType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.ZoneAddressType;


/**
 * This interface is for Sabrix external tax calls
 *
 */
@Component("doterraTaxService")
public class DoterraTaxServiceImpl implements DoterraTaxService
{

	@Resource(name = "doterraIntegrationConfigurationPropertiesService")
	private DoterraConfigurationPropertiesService doterraConfigurationPropertiesService;

	@Resource(name = "modelService")
	private ModelService modelService;

	private static final Logger LOG = Logger.getLogger(DoterraTaxServiceImpl.class);

	private static ObjectPool<ProxyWrapper<TaxCalculationService>> pool = null;

	private static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");

	private OndemandHystrixCommandFactory ondemandHystrixCommandFactory;
	private OndemandHystrixCommandConfiguration hystrixCommandConfig;

	private Service service = null;

	protected OndemandHystrixCommandFactory getOndemandHystrixCommandFactory()
	{
		return ondemandHystrixCommandFactory;
	}

	@Required
	public void setOndemandHystrixCommandFactory(final OndemandHystrixCommandFactory ondemandHystrixCommandFactory)
	{
		this.ondemandHystrixCommandFactory = ondemandHystrixCommandFactory;
	}

	protected OndemandHystrixCommandConfiguration getHystrixCommandConfig()
	{
		return hystrixCommandConfig;
	}

	@Required
	public void setHystrixCommandConfig(final OndemandHystrixCommandConfiguration hystrixCommandConfig)
	{
		this.hystrixCommandConfig = hystrixCommandConfig;
	}

	/**
	 * This operation calls External Sabrix Tax service to get tax data for Invoice
	 *
	 */
	@Override
	public OutdataInvoiceType taxCalculation(final AbstractOrderModel cart, final Map<Integer, Map<String, String>> priceDetails,
			final UserGroupModel userGroup) throws Exception
	{
		LOG.warn("***************TAX CALCULATION BEGINS******************");
		/* Create the service instance */
		LOG.warn("DoterraTaxServiceImpl:Order Entries" + cart.getEntries());
		LOG.warn("DoterraTaxServiceImpl:User Group Name" + userGroup.getName());
		boolean isSuccess = false;
		final Boolean isTaxExempted = Boolean.FALSE;
		OutdataInvoiceType outDataInvoice = new OutdataInvoiceType();
		String userState = "";
		ProxyWrapper<TaxCalculationService> wrapper = null;
		try
		{
			final String url = Config.getString("tax.connection.url", "") + "?wsdl";

			final int connectionRetry = Integer.parseInt("2");
			//final String connectionTimeout = doterraConfigurationPropertiesService.getPropertyForKey("tax.connection.timeout");

			//final boolean isAvailable = isServiceConnectionAvailable();

			//retryServiceConnectionOnFirstFailure(connectionRetry, isAvailable);

			wrapper = fetchServicePort(url);
			final TaxCalculationService servicePort = wrapper.getItem();

			final Binding binding = fetchBindingObject(url, servicePort);

			final TaxCalculationRequest taxRequest = new TaxCalculationRequest();
			final long a1 = java.lang.System.currentTimeMillis();

			final IndataType inData = new IndataType();
			final IndataInvoiceType invoiceType = new IndataInvoiceType();
			final List<IndataInvoiceType> multipleInvoiceType = setUpIndataAndInvoiceTypeData(cart, inData, invoiceType);

			final long a2 = java.lang.System.currentTimeMillis();
			LOG.warn("DoterraTaxServiceImpl: Time Took To Set The Invoice Type Data" + (int) ((a2 - a1) / 1000) % 60);

			String userGrp = "";
			if (userGroup != null)
			{
				userGrp = getAccountTypeFromUserGroup(userGroup);
			}

			final List<UserElementType> userElementsInvoice = invoiceType.getUSERELEMENT();
			final UserElementType userElement = null;

			final UserModel userModel = cart.getUser();
			if (null != userModel)
			{
				LOG.warn("DoterraTaxServiceImpl:UserModel " + userModel.getUid());

				final DoterraCustomerModel customerModel = (DoterraCustomerModel) userModel;

				invoiceType.setCUSTOMERNAME(userModel.getName());
				invoiceType.setCUSTOMERNUMBER(userModel.getUid());

				populateRegistrationDataInInvoiceType(invoiceType, userModel);

				populateShipToAddressInInvoiceType(cart, invoiceType, userModel);

				userState = determineUserState(userState, invoiceType);

				populateUserElementInInvoiceTypeBasedOnTaxExemption(cart, userState, userElementsInvoice, userModel);
			}

			populateUserElementInInvoiceTypeBasedOnCartInvoice(cart, userElementsInvoice);

			populateUserElementInInvoiceTypeBasedOnTaxFallbackMode(cart, userElementsInvoice);

			populateUserElementInInvoiceTypeBasedOnUserGroup(userGrp, userElementsInvoice);

			String invoiceNumber = cart.getCode().replaceFirst("^0*", "");
			if (null == cart.getOrderType() || cart.getOrderType().equals(DoterraOrderTypeEnum.LRP))
			{
				invoiceNumber = invoiceNumber + "-"
						+ DoterraIntegrationConstants.DATE_FORMATTER_YYYYMMDD.format(cart.getCreationtime());
			}

			invoiceType.setINVOICENUMBER(invoiceNumber);

			LOG.warn("DoterraTaxServiceImpl:Invoice Number" + invoiceType.getINVOICENUMBER());

			LOG.warn("DoterraTaxServiceImpl:ShipTo address" + invoiceType.getSHIPTO());

			populateBillToAddressForInvoiceType(cart, invoiceType);

			populateShipFromAddressForInvoiceType(cart, invoiceType);

			populateUserElementInInvoiceTypeBasedOnOrderType(cart, userElementsInvoice);

			populateUserElementInInvoiceTypeBasedOnOrderStatus(cart, userElementsInvoice);

			populateUserElementInInvoiceTypeBasedOnPersonalConsumption(cart, userElementsInvoice);

			final List<IndataLineType> multipleLineItems = invoiceType.getLINE();
			final List<AbstractOrderEntryModel> entries = cart.getEntries();

			IndataLineType lineItem;
			List<UserElementType> userElements = null;
			final UserElementType userEle = null;
			AbstractOrderEntryModel orderEntry;
			List<Integer> entriesNoList = new ArrayList<Integer>();
			if (null != entries && entries.size() > 0)
			{
				final long e1 = java.lang.System.currentTimeMillis();
				for (int i = 0; i < entries.size(); i++)
				{
					orderEntry = entries.get(i);

					final DoterraProductModel product = (DoterraProductModel) orderEntry.getProduct();

					final double totalDiscount = calculateTotalDiscount(orderEntry);

					lineItem = new IndataLineType();
					lineItem.setID(String.valueOf((orderEntry.getEntryNumber().intValue() + 1)));
					entriesNoList.add(orderEntry.getEntryNumber());
					lineItem.setLINENUMBER(new BigDecimal(orderEntry.getEntryNumber().intValue() + 1));
					lineItem.setPRODUCTCODE(product.getCode());
					//lineItem.setDESCRIPTION(product.getDescription());
					lineItem.setDESCRIPTION(product.getName());
					userElements = lineItem.getUSERELEMENT();//

					populateWholesaleAndRetailAmount(cart, priceDetails, lineItem, userElements, orderEntry, totalDiscount);

					populateQuantityForLineItem(lineItem, orderEntry);

					multipleLineItems.add(lineItem);
				}
				final long e2 = java.lang.System.currentTimeMillis();
				LOG.warn("DoterraTaxServiceImpl: Time Took For Discount, Wholesale Amt, Retail Amt Calculation & Line Item Sets, "
						+ "User Element Sets / For Loop" + (int) ((e2 - e1) / 1000) % 60);
			}
			else
			{
				return null;
			}
			int entriesSize = Collections.max(entriesNoList).intValue() + 1;

			final double shippingDiscount = calculateShippingDiscount(cart);

			final double otherDiscounts = calculateOtherDiscounts(cart, shippingDiscount);

			entriesSize = populateDiscountsInLineItems(multipleLineItems, entriesSize, otherDiscounts);

			populateDeliveryCostInLineItems(cart, multipleLineItems, entriesSize, shippingDiscount);

			multipleInvoiceType.add(invoiceType);
			taxRequest.setINDATA(inData);

			// Add the logging handler
			List handlerList = binding.getHandlerChain();
			if (handlerList == null)
			{
				handlerList = new ArrayList();
			}

			final TaxLogger taxLogger = new TaxLogger();
			handlerList.add(taxLogger);
			binding.setHandlerChain(handlerList);

			final long start = System.currentTimeMillis();
			final TaxCalculationResponse taxResponse = calculateTax(taxRequest, servicePort);
			final long delta = System.currentTimeMillis() - start;
			LOG.info("Tax call - time interval : " + delta);
			final OutdataType outData = taxResponse.getOUTDATA();
			if (null != outData)
			{
				final OutdataRequestStatusType requestStatus = outData.getREQUESTSTATUS();
				if (requestStatus.isISSUCCESS())
				{
					final List<OutdataInvoiceType> outDataInvoiceType = outData.getINVOICE();
					if (null != outDataInvoiceType)
					{
						outDataInvoice = outDataInvoiceType.get(0);
						if (null != outDataInvoice)
						{
							LOG.warn("DoterraTaxServiceImpl:Tax claculation is successful");
							isSuccess = true;
						}
					}
				}
				else if (null != requestStatus.getERROR())
				{
					final List<OutdataErrorType> taxErrorList = requestStatus.getERROR();
					if (taxErrorList.size() > 0)
					{
						for (final OutdataErrorType errorType : taxErrorList)
						{
							isSuccess = false;
							LOG.error("DoterraTaxServiceImpl  :" + errorType.getCODE() + "  " + errorType.getDESCRIPTION());
						}
					}
				}
			}
		}
		catch (final TaxCalculationFault_Exception ex)
		{
			isSuccess = false;
			LOG.error("DoterraTaxServiceImpl  Tax Exception:", ex);
		}
		catch (final Exception exception)
		{
			isSuccess = false;
			LOG.error("DoterraTaxServiceImpl  Exception:", exception);
			throw exception;
		}
		finally
		{
			if (wrapper != null && pool != null)
			{
				try
				{
					pool.returnObject(wrapper);
				}
				catch (final Exception e)
				{
					LOG.error(e);
				}
			}
		}
		if (isSuccess)
		{
			return outDataInvoice;
		}
		else
		{
			return null;
		}
	}

	private void populateDeliveryCostInLineItems(final AbstractOrderModel cart, final List<IndataLineType> multipleLineItems,
			int entriesSize, final double shippingDiscount)
	{
		IndataLineType lineItem;
		List<UserElementType> userElements;
		UserElementType userEle;
		if (null != cart.getDeliveryCost() && cart.getDeliveryCost().doubleValue() != 0.0)
		{
			lineItem = new IndataLineType();
			entriesSize = entriesSize + 1;
			lineItem.setID(String.valueOf(entriesSize));
			lineItem.setLINENUMBER(new BigDecimal(entriesSize));
			lineItem.setPRODUCTCODE(DoterraIntegrationConstants.FREIGHT);
			double deliveryCost = 0.0;
			if (shippingDiscount != 0.0)
			{
				deliveryCost = cart.getDeliveryCost() - shippingDiscount;
			}
			else
			{
				deliveryCost = cart.getDeliveryCost();
			}
			lineItem.setGROSSAMOUNT(String.valueOf(deliveryCost));
			userElements = lineItem.getUSERELEMENT();//
			userEle = new UserElementType();
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_11);//Retail amount
			userEle.setVALUE(String.valueOf(deliveryCost));//Retail amount
			userElements.add(userEle);
			userEle = new UserElementType();
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_12);//Retail amount
			userEle.setVALUE(String.valueOf(deliveryCost));//Retail amount
			userElements.add(userEle);
			multipleLineItems.add(lineItem);
		}
	}

	private int populateDiscountsInLineItems(final List<IndataLineType> multipleLineItems, int entriesSize,
			final double otherDiscounts)
	{
		IndataLineType lineItem;
		List<UserElementType> userElements;
		UserElementType userEle;
		if (otherDiscounts != 0.0)
		{
			lineItem = new IndataLineType();
			entriesSize = entriesSize + 1;
			lineItem.setID(String.valueOf(entriesSize));
			lineItem.setLINENUMBER(new BigDecimal(entriesSize));
			lineItem.setPRODUCTCODE(DoterraIntegrationConstants.DISCOUNT_HEADER_ITEM);
			lineItem.setGROSSAMOUNT("-" + otherDiscounts);
			userElements = lineItem.getUSERELEMENT();//
			userEle = new UserElementType();
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_11);//Retail amount
			userEle.setVALUE("-" + otherDiscounts);//Retail amount
			userElements.add(userEle);
			userEle = new UserElementType();
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_12);//Retail amount
			userEle.setVALUE("-" + otherDiscounts);//Retail amount
			userElements.add(userEle);
			multipleLineItems.add(lineItem);
		}
		return entriesSize;
	}

	private double calculateOtherDiscounts(final AbstractOrderModel cart, final double shippingDiscount)
	{
		double otherDiscounts = cart.getTotalDiscounts().doubleValue();
		if (shippingDiscount > 0.0)
		{
			otherDiscounts = otherDiscounts - shippingDiscount;
		}
		return otherDiscounts;
	}

	private double calculateShippingDiscount(final AbstractOrderModel cart)
	{
		double shippingDiscount = 0.0;
		final List<DiscountValue> globalDiscounts = cart.getGlobalDiscountValues();
		if (null != globalDiscounts && globalDiscounts.size() > 0)
		{
			final long f1 = java.lang.System.currentTimeMillis();
			for (final DiscountValue discount : globalDiscounts)
			{
				if (null != discount.getCode() && discount.getCode().contains(("Shipping")))
				{
					shippingDiscount = shippingDiscount + discount.getAppliedValue();
				}
			}
			final long f2 = java.lang.System.currentTimeMillis();
			LOG.warn("DoterraTaxServiceImpl: Global Discounts Applicable / For loop" + (int) ((f2 - f1) / 1000) % 60);
		}
		return shippingDiscount;
	}

	private void populateQuantityForLineItem(final IndataLineType lineItem, final AbstractOrderEntryModel orderEntry)
	{
		if (null != orderEntry.getQuantity())
		{
			lineItem.setQUANTITY(BigInteger.valueOf(orderEntry.getQuantity().longValue()));
			final QuantitiesType quantities = new QuantitiesType();
			final List<QuantityType> quantityList = quantities.getQUANTITY();
			final QuantityType quantity = new QuantityType();
			quantity.setAMOUNT(String.valueOf(orderEntry.getQuantity()));
			quantityList.add(quantity);
			lineItem.setQUANTITIES(quantities);
		}
	}

	private void populateWholesaleAndRetailAmount(final AbstractOrderModel cart,
			final Map<Integer, Map<String, String>> priceDetails, final IndataLineType lineItem,
			final List<UserElementType> userElements, final AbstractOrderEntryModel orderEntry, final double totalDiscount)
	{
		UserElementType userEle;
		if (null != orderEntry.getPointConsumed() && orderEntry.getPointConsumed().doubleValue() > 0.0)
		{
			lineItem.setGROSSAMOUNT("0");
			userEle = new UserElementType();
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_12);//Wholesale amount
			userEle.setVALUE("0");//wholesale amount
			userElements.add(userEle);
			userEle = new UserElementType();
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_11);//Retail amount
			userEle.setVALUE("0");//wholesale amount
			userElements.add(userEle);
		}
		else
		{
			lineItem.setGROSSAMOUNT(String.valueOf(orderEntry.getTotalPrice().doubleValue()));
			if (null != cart.getOrderType() && (cart.getOrderType().equals(DoterraOrderTypeEnum.FREE)
					|| cart.getOrderType().equals(DoterraOrderTypeEnum.REPLACEMENT)))
			{
				lineItem.setGROSSAMOUNT("0.0");
			}

			final Map<String, String> priceData = priceDetails.get(orderEntry.getEntryNumber());

			calculateWholesaleAmount(cart, userElements, orderEntry, totalDiscount, priceData);

			calculateRetailAmount(cart, userElements, orderEntry, totalDiscount, priceData);
		}
	}

	private void calculateRetailAmount(final AbstractOrderModel cart, final List<UserElementType> userElements,
			final AbstractOrderEntryModel orderEntry, final double totalDiscount, final Map<String, String> priceData)
	{
		UserElementType userEle;
		String retailAmount = priceData.get("retailPrice");
		if (null != retailAmount && !retailAmount.equals("0.0"))
		{
			userEle = new UserElementType();
			if (totalDiscount != 0.0)
			{
				retailAmount = String
						.valueOf((Double.parseDouble(retailAmount) * orderEntry.getQuantity().doubleValue()) - totalDiscount);
			}
			else
			{
				retailAmount = String.valueOf(Double.parseDouble(retailAmount) * orderEntry.getQuantity().doubleValue());
			}
			if (null != cart.getOrderType() && (cart.getOrderType().equals(DoterraOrderTypeEnum.FREE)
					|| cart.getOrderType().equals(DoterraOrderTypeEnum.REPLACEMENT)))
			{
				retailAmount = "0.0";
			}
			LOG.warn("DoterraTaxServiceImpl:retailAmount" + retailAmount);
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_11);//Retail amount
			userEle.setVALUE(retailAmount);//Retail amount
			userElements.add(userEle);
		}
	}

	private void calculateWholesaleAmount(final AbstractOrderModel cart, final List<UserElementType> userElements,
			final AbstractOrderEntryModel orderEntry, final double totalDiscount, final Map<String, String> priceData)
	{
		UserElementType userEle;
		String wholesaleAmount = priceData.get("wholesalePrice");

		if (null != wholesaleAmount && !wholesaleAmount.equals("0.0"))
		{
			userEle = new UserElementType();
			if (totalDiscount != 0.0)
			{
				wholesaleAmount = String
						.valueOf((Double.parseDouble(wholesaleAmount) * orderEntry.getQuantity().doubleValue()) - totalDiscount);
			}
			else
			{
				wholesaleAmount = String.valueOf(Double.parseDouble(wholesaleAmount) * orderEntry.getQuantity().doubleValue());
			}
			if (null != cart.getOrderType() && (cart.getOrderType().equals(DoterraOrderTypeEnum.FREE)
					|| cart.getOrderType().equals(DoterraOrderTypeEnum.REPLACEMENT)))
			{
				wholesaleAmount = "0.0";
			}
			LOG.warn("DoterraTaxServiceImpl:wholesaleAmount" + wholesaleAmount);
			userEle.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_12);//Wholesale amount
			userEle.setVALUE(wholesaleAmount);//wholesale amount
			userElements.add(userEle);
		}
	}

	private double calculateTotalDiscount(final AbstractOrderEntryModel orderEntry)
	{
		final List<DiscountValue> discountValues = orderEntry.getDiscountValues();
		double totalDiscount = 0.0;
		if (null != discountValues && discountValues.size() > 0)
		{
			for (final DiscountValue discount : discountValues)
			{
				totalDiscount = totalDiscount + discount.getAppliedValue();
			}
		}
		LOG.warn("DoterraTaxServiceImpl:Total discounts" + totalDiscount);
		return totalDiscount;
	}

	private void populateUserElementInInvoiceTypeBasedOnPersonalConsumption(final AbstractOrderModel cart,
			final List<UserElementType> userElementsInvoice)
	{
		UserElementType userElement;
		if (cart.getIsPersonalConsumption() != null && cart.getIsPersonalConsumption().booleanValue() == true)
		{
			userElement = new UserElementType();
			userElement.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_2);//personal consumption
			userElement.setVALUE("X");//
			userElementsInvoice.add(userElement);
		}
	}

	private void populateUserElementInInvoiceTypeBasedOnOrderStatus(final AbstractOrderModel cart,
			final List<UserElementType> userElementsInvoice)
	{
		UserElementType userElement;
		if (null != cart.getStatus() && null != cart.getStatus().getCode())
		{
			userElement = new UserElementType();
			userElement.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_6);//order status
			userElement.setVALUE(String.valueOf(cart.getStatus().getCode()));//
			userElementsInvoice.add(userElement);
			LOG.warn("DoterraTaxServiceImpl:Order status" + cart.getStatus().getCode());
		}
	}

	private void populateUserElementInInvoiceTypeBasedOnOrderType(final AbstractOrderModel cart,
			final List<UserElementType> userElementsInvoice)
	{
		UserElementType userElement;
		if (null != cart.getOrderType())
		{
			userElement = new UserElementType();
			userElement.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_5);//OrderType
			userElement.setVALUE(String.valueOf(cart.getOrderType()));//
			userElementsInvoice.add(userElement);
			LOG.warn("DoterraTaxServiceImpl:Order type" + cart.getOrderType());
		}
	}

	private void populateUserElementInInvoiceTypeBasedOnUserGroup(final String userGrp,
			final List<UserElementType> userElementsInvoice)
	{
		UserElementType userElement;
		if (StringUtils.isNotBlank(userGrp))
		{
			userElement = new UserElementType();
			userElement.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_4);//User status
			userElement.setVALUE(userGrp);//
			userElementsInvoice.add(userElement);
			LOG.warn("DoterraTaxServiceImpl:User Status" + userGrp);
		}
	}

	private void populateUserElementInInvoiceTypeBasedOnTaxFallbackMode(final AbstractOrderModel cart,
			final List<UserElementType> userElementsInvoice)
	{
		UserElementType userElement;
		if (null != cart.getTaxFallbackMode())
		{
			userElement = new UserElementType();
			userElement.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_20);//Cart number for order/Order number for return orders
			userElement.setVALUE(cart.getTaxFallbackMode());//
			userElementsInvoice.add(userElement);
			LOG.warn("DoterraTaxServiceImpl: Cart Tax Fall Back Mode" + cart.getTaxFallbackMode());
		}
	}

	private void populateUserElementInInvoiceTypeBasedOnCartInvoice(final AbstractOrderModel cart,
			final List<UserElementType> userElementsInvoice)
	{
		UserElementType userElement;
		if (null != cart.getInvoice())
		{
			userElement = new UserElementType();
			userElement.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_18);//Cart number for order/Order number for return orders
			userElement.setVALUE(cart.getInvoice());//
			userElementsInvoice.add(userElement);
			LOG.warn("DoterraTaxServiceImpl: Cart Invoice" + cart.getInvoice());
		}
	}

	private void populateUserElementInInvoiceTypeBasedOnTaxExemption(final AbstractOrderModel cart, final String userState,
			final List<UserElementType> userElementsInvoice, final UserModel userModel)
	{
		Boolean isTaxExempted = false;
		UserElementType userElement;
		final Collection taxExemptionIds = userModel.getTaxExemptions();
		final long d1 = java.lang.System.currentTimeMillis();
		cart.setIsTaxExempted(isTaxExempted);
		if (null != taxExemptionIds && taxExemptionIds.size() > 0)
		{
			final Iterator itr = taxExemptionIds.iterator();
			while (itr.hasNext())
			{
				final TaxExemptionsModel taxExemption = (TaxExemptionsModel) itr.next();
				if (null != taxExemption.getTaxExemptionId() && null != taxExemption.getTaxExemptedState()
						&& taxExemption.getTaxExemptedState().trim().equalsIgnoreCase(userState.trim()))
				{
					try
					{
						Date current = new Date();
						current = sdf.parse(sdf.format(current));
						Date givenDate = taxExemption.getTaxExemptionExpirationDate();
						//compare both dates
						if (current.after(givenDate))
						{
							LOG.error("Tax Exemption is expired for state:" + userState);
						}
						else
						{
							userElement = new UserElementType();
							userElement.setNAME(DoterraIntegrationConstants.CUSTOM_ATTRIBUTE_17);//Tax Exemption
							userElement.setVALUE(taxExemption.getTaxExemptionId().trim());//
							userElementsInvoice.add(userElement);
							isTaxExempted = Boolean.TRUE;
							cart.setIsTaxExempted(isTaxExempted);
							//modelService.save(cart);
							break;
						}
					}
					catch (Exception ex)
					{
						LOG.error("Error in parsing Tax exemption date", ex);
					}
				}
			}
		}
		final long d2 = java.lang.System.currentTimeMillis();
		LOG.warn("DoterraTaxServiceImpl: Saving the Cart With Tax Exemption / While Loop" + (int) ((d2 - d1) / 1000) % 60);
	}

	private String determineUserState(String userState, final IndataInvoiceType invoiceType)
	{
		if (null != invoiceType.getSHIPTO())
		{
			if (null != invoiceType.getSHIPTO().getSTATE())
			{
				userState = invoiceType.getSHIPTO().getSTATE().trim();
			}
			else
			{
				userState = invoiceType.getSHIPTO().getPROVINCE().trim();
			}
		}
		return userState;
	}

	private void populateRegistrationDataInInvoiceType(final IndataInvoiceType invoiceType, final UserModel userModel)
	{
		final RegistrationsType registrations = new RegistrationsType();
		final List<String> buyerRole = registrations.getBUYERROLE();
		if (null != userModel.getTaxId1())
		{
			LOG.warn("DoterraTaxServiceImpl:User Tax Exemption id" + userModel.getTaxId1());
			buyerRole.add(userModel.getTaxId1());
		}
		if (null != userModel.getTaxId2())
		{
			LOG.warn("DoterraTaxServiceImpl:User Tax Exemption id" + userModel.getTaxId2());
			buyerRole.add(userModel.getTaxId2());
		}
		if (buyerRole.size() == 2)
		{
			invoiceType.setREGISTRATIONS(registrations);
		}
	}

	private void populateShipToAddressInInvoiceType(final AbstractOrderModel cart, final IndataInvoiceType invoiceType,
			final UserModel userModel)
	{
		final AddressModel address = cart.getDeliveryAddress();
		final ZoneAddressType shipTo = new ZoneAddressType();
		if (null != address)
		{
			final long b1 = java.lang.System.currentTimeMillis();
			populateShiptoAddress(address, invoiceType);
			final long b2 = java.lang.System.currentTimeMillis();
			LOG.warn("DoterraTaxServiceImpl: Time Took To Execute populateShipAddress Method" + (int) ((b2 - b1) / 1000) % 60);
		}
		else if (null != userModel.getAddresses() && !userModel.getAddresses().isEmpty())
		{
			final Collection<AddressModel> addresses = userModel.getAddresses();

			boolean isRetrieved = false;
			final Iterator<AddressModel> addressModel = addresses.iterator();
			final long c1 = java.lang.System.currentTimeMillis();
			while (addressModel.hasNext())
			{
				if (isRetrieved)
				{
					break;
				}
				final AddressModel userAddress = addressModel.next();
				if (userAddress.getShippingAddress().booleanValue() && !isRetrieved)
				{
					populateShiptoAddress(userAddress, invoiceType);
					isRetrieved = true;
				}
				else if (userAddress.getBillingAddress().booleanValue() && !isRetrieved)
				{
					populateShiptoAddress(userAddress, invoiceType);
					isRetrieved = true;
				}
				else if (userAddress.getContactAddress().booleanValue() && !isRetrieved)
				{
					populateShiptoAddress(userAddress, invoiceType);
					isRetrieved = true;
				}
			}
			final long c2 = java.lang.System.currentTimeMillis();
			LOG.warn("DoterraTaxServiceImpl: Time Taken To Execute While Loop To Populate ShipToAddress"
					+ (int) ((c2 - c1) / 1000) % 60);
		}
		else
		{
			shipTo.setCOUNTRY("US");
			shipTo.setSTATE("CA");
			shipTo.setCITY("Beverly Hills");
			shipTo.setPOSTCODE("90210");
			invoiceType.setSHIPTO(shipTo);
		}
	}

	private void populateShipFromAddressForInvoiceType(final AbstractOrderModel cart, final IndataInvoiceType invoiceType)
	{
		final ZoneAddressType shipFrom = new ZoneAddressType();

		if (null != cart.getDeliveryMode() && null != cart.getDeliveryMode().getCode())
		{
			final String deliveryModeCode = cart.getDeliveryMode().getCode();
			if ((DoterraIntegrationConstants.DOTERRA_PRODUCT_CENTER_DELIVERY_CODE).equalsIgnoreCase(deliveryModeCode))
			{
				shipFrom.setCOUNTRY(DoterraIntegrationConstants.COUNTRY_US);
				shipFrom.setSTATE(DoterraIntegrationConstants.SHIP_FROM_STATE);
				shipFrom.setCITY(DoterraIntegrationConstants.SHIP_FROM_CITY_PC);
				shipFrom.setPOSTCODE(DoterraIntegrationConstants.SHIP_FROM_POSTCODE_PC);
				invoiceType.setSHIPFROM(shipFrom);
			}
			else
			{
				shipFrom.setCOUNTRY(DoterraIntegrationConstants.COUNTRY_US);
				shipFrom.setSTATE(DoterraIntegrationConstants.SHIP_FROM_STATE);
				shipFrom.setCITY(DoterraIntegrationConstants.SHIP_FROM_CITY);
				shipFrom.setPOSTCODE(DoterraIntegrationConstants.SHIP_FROM_POSTCODE);
				invoiceType.setSHIPFROM(shipFrom);
			}
		}
	}

	private void populateBillToAddressForInvoiceType(final AbstractOrderModel cart, final IndataInvoiceType invoiceType)
	{
		CountryModel country;
		final ZoneAddressType billToAddress = new ZoneAddressType();
		final AddressModel paymentAddress = cart.getPaymentAddress();

		if (null != paymentAddress)
		{
			country = paymentAddress.getCountry();

			if (null != country)
			{
				billToAddress.setCOUNTRY(country.getIsocode());
				billToAddress.setSTATE(paymentAddress.getRegion().getIsocodeShort());
				billToAddress.setCITY(paymentAddress.getTown());
				billToAddress.setPOSTCODE(paymentAddress.getPostalcode());
				invoiceType.setBILLTO(billToAddress);
			}

		}
	}

	//	private void retryServiceConnectionOnFirstFailure(final int connectionRetry, boolean isAvailable) throws Exception
	//	{
	//		if (!isAvailable)
	//		{
	//			for (int i = 1; i <= connectionRetry; i++)
	//			{
	//				isAvailable = isServiceConnectionAvailable();
	//				if (isAvailable)
	//				{
	//					LOG.warn("DoterraTaxServiceImpl: OITD service is up");
	//					break;
	//				}
	//				else
	//				{
	//					LOG.warn("DoterraTaxServiceImpl: OITD service is not up");
	//					if (i == connectionRetry)
	//					{
	//						throw new Exception("TaxException");
	//					}
	//				}
	//			}
	//		}
	//	}

	private List<IndataInvoiceType> setUpIndataAndInvoiceTypeData(final AbstractOrderModel cart, final IndataType inData,
			final IndataInvoiceType invoiceType)
	{
		final VersionType version = inData.getVersion();
		inData.setVersion(version.G);
		final List<IndataInvoiceType> multipleInvoiceType = inData.getINVOICE();
		invoiceType.setCALLINGSYSTEMNUMBER(Config.getString("tax.calling.system.number", ""));
		invoiceType.setEXTERNALCOMPANYID(DoterraIntegrationConstants.EXTERNAL_COMPANY_ID);
		invoiceType.setHOSTSYSTEM(DoterraIntegrationConstants.HOST_SYSTEM);
		invoiceType.setCALCULATIONDIRECTION(DoterraIntegrationConstants.CALCULATION_DIRECTION);
		invoiceType.setCOMPANYROLE(DoterraIntegrationConstants.COMPANY_ROLE);
		invoiceType.setCURRENCYCODE(cart.getCurrency().getIsocode());
		invoiceType.setINVOICEDATE(DoterraIntegrationConstants.DATE_FORMATTER_TIME.format(new Date()));//current date in 'yyyy-MM-dd HH:mm:ss' format
		invoiceType.setISAUDITED(DoterraIntegrationConstants.IS_AUDITED);
		invoiceType.setTRANSACTIONTYPE(DoterraIntegrationConstants.TRANSACTION_TYPE);
		invoiceType.setISREVERSED(DoterraIntegrationConstants.IS_AUDITED);
		invoiceType.setISREPORTED(DoterraIntegrationConstants.IS_AUDITED);
		return multipleInvoiceType;
	}

	private Binding fetchBindingObject(final String url, final TaxCalculationService servicePort)
	{
		final BindingProvider bp = (BindingProvider) servicePort;

		//Set timeout until a connection is established
		final long ti1 = java.lang.System.currentTimeMillis();
		//bp.getRequestContext().put("javax.xml.ws.client.connectionTimeout", connectionTimeout);
		final long ti2 = java.lang.System.currentTimeMillis();
		LOG.warn(
				"DoterraTaxServiceImpl: Time Took To Set Timeout Until A Connection Established" + (int) ((ti2 - ti1) / 1000) % 60);

		//Set timeout until the response is received
		final long p1 = java.lang.System.currentTimeMillis();
		//bp.getRequestContext().put("javax.xml.ws.client.receiveTimeout", connectionTimeout);
		bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
		final long p2 = java.lang.System.currentTimeMillis();
		LOG.warn("DoterraTaxServiceImpl: Time Took To Set Timeout Until A Response Is Received" + (int) ((p2 - p1) / 1000) % 60);
		final Binding binding = bp.getBinding();
		return binding;
	}

	private ProxyWrapper<TaxCalculationService> fetchServicePort(final String url) throws Exception, MalformedURLException
	{

		final QName ns = new QName("http://www.sabrix.com/services/taxcalculationservice/2011-09-01", "TaxCalculationService");
		if (service == null)
		{
			URL wsdl = null;
			try
			{
				wsdl = new URL(url);
			}
			catch (final MalformedURLException mue)
			{
				LOG.error(mue);
				throw new Exception("TaxException");
			}
			synchronized (this)
			{
				if (service == null)
				{
					service = fetchService(wsdl, ns);
					final GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
					poolConfig.setMaxTotal(50);
					pool = new GenericObjectPool<ProxyWrapper<TaxCalculationService>>(new DoterraPoolFactory(service), poolConfig);
				}
			}
		}

		ProxyWrapper<TaxCalculationService> wrapper = null;
		wrapper = pool.borrowObject();

		//synchronized (service)
		//{
		//	final QName qname = new QName("http://www.sabrix.com/services/taxcalculationservice/2011-09-01",
		//			"TaxCalculationServicePort");
		//	servicePort = service.getPort(qname, TaxCalculationService.class);
		//}

		return wrapper;
	}

	//	private boolean isServiceConnectionAvailable()
	//	{
	//		final long time1 = java.lang.System.currentTimeMillis();
	//		final boolean isAvailable = checkServiceConnection();
	//		final long time2 = java.lang.System.currentTimeMillis();
	//		final int seconds = (int) ((time2 - time1) / 1000) % 60;
	//		LOG.info("Time for servcie connection  seconds: " + seconds);
	//		LOG.warn("DoterraTaxServiceImpl: Time for Tax Connection Service 1:" + seconds);
	//		return isAvailable;
	//	}

	private TaxCalculationResponse calculateTax(final TaxCalculationRequest taxRequest, final TaxCalculationService servicePort)
			throws TaxCalculationFault_Exception
	{
		final TaxCalculationResponse taxResponse = getOndemandHystrixCommandFactory()
				.newCommand(getHystrixCommandConfig(), new HystrixExecutable<TaxCalculationResponse>()
				{
					@Override
					public TaxCalculationResponse runEvent()
					{
						TaxCalculationResponse taxResponse = null;
						try
						{
							LOG.error("I am calling TAX service START " + taxRequest.toString());
							taxResponse = servicePort.calculateTax(taxRequest);
							LOG.error("I am calling TAX service END ");
						}
						catch (final TaxCalculationFault_Exception ex)
						{
							LOG.error("Problem connecting to Sabrix server " + ex.getMessage() + " ", ex);
							if (ex.getFaultInfo() != null)
							{
								LOG.error("Additional Message " + ex.getFaultInfo().getAdditionalMessage());
								LOG.error("Error location " + ex.getFaultInfo().getErrorLocation());
								LOG.error("Error source " + ex.getFaultInfo().getErrorSource());
							}
						}
						catch (final Exception ex)
						{
							LOG.error("Problem connecting to Sabrix server " + ex.getMessage() + " ", ex);
						}
						return taxResponse;
					}

					@Override
					public TaxCalculationResponse fallbackEvent()
					{
						return null;
					}

					@Override
					public TaxCalculationResponse defaultEvent()
					{
						return null;
					}
				}).execute();

		if (taxResponse == null)
		{
			LOG.error("Problem connecting to Sabrix server");
			throw new TaxCalculationFault_Exception("Problem connecting to Sabrix server", null);
		}
		return taxResponse;
	}

	private void populateShiptoAddress(final AddressModel address, final IndataInvoiceType invoiceType)
	{
		final ZoneAddressType shipTo = new ZoneAddressType();
		final CountryModel country = address.getCountry();

		if (null != country)
		{
			shipTo.setCOUNTRY(country.getIsocode());
			if (country.getIsocode().equalsIgnoreCase(DoterraIntegrationConstants.COUNTRY_US))
			{
				shipTo.setSTATE(address.getRegion().getIsocodeShort());
			}
			else
			{
				shipTo.setPROVINCE(address.getRegion().getIsocodeShort());
			}
			shipTo.setCITY(address.getTown());
			final String postalCode = address.getPostalcode();
			if (postalCode.contains("-"))
			{
				final String[] zipCodes = postalCode.split("-");
				shipTo.setPOSTCODE(zipCodes[0]);
				shipTo.setGEOCODE(zipCodes[1]);
			}
			else
			{
				shipTo.setPOSTCODE(postalCode);
			}
			invoiceType.setSHIPTO(shipTo);
		}
	}

	@Override
	public boolean checkServiceConnection()
	{
		final String serviceUrl = Config.getString("tax.connection.url.check", "http://10.16.47.26:8080/sabrix//taxproduct");
		//final String connectionTimeout = doterraConfigurationPropertiesService.getPropertyForKey("tax.connection.timeout");

		final Boolean serviceStatus = getOndemandHystrixCommandFactory()
				.newCommand(getHystrixCommandConfig(), new HystrixExecutable<Boolean>()
				{
					@Override
					public Boolean runEvent()
					{
						Boolean status = new Boolean(false);
						try
						{
							final URL url = new URL(serviceUrl);
							final long time1 = java.lang.System.currentTimeMillis();
							final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
							final long time2 = java.lang.System.currentTimeMillis();
							LOG.debug("DoterraTaxServiceImpl: Time took to open connection" + (int) ((time2 - time1) / 1000) % 60);
							connection.setRequestProperty("Connection", "close");
							//connection.setConnectTimeout(Integer.parseInt(connectionTimeout)); // Timeout 2 seconds
							final long t1 = java.lang.System.currentTimeMillis();
							connection.connect();
							final long t2 = java.lang.System.currentTimeMillis();
							LOG.debug("DoterraTaxServiceImpl: Time took for actual connection" + (int) ((t2 - t1) / 1000) % 60);

							// If the web service is available
							if (connection.getResponseCode() == 200)
							{
								status = new Boolean(true);
							}
							connection.disconnect();
						}
						catch (final Exception ex)
						{
							LOG.warn("Sabrix connection issue", ex);
						}
						return status;
					}

					@Override
					public Boolean fallbackEvent()
					{
						return new Boolean(false);
					}

					@Override
					public Boolean defaultEvent()
					{
						return new Boolean(false);
					}
				}).execute();

		return serviceStatus.booleanValue();
	}

	private Service fetchService(final URL wsdl, final QName ns) throws Exception
	{
		final Service service = getOndemandHystrixCommandFactory()
				.newCommand(getHystrixCommandConfig(), new HystrixExecutable<Service>()
				{
					@Override
					public Service runEvent()
					{
						Service service = null;
						try
						{
							service = Service.create(wsdl, ns);
						}
						catch (final Exception ex)
						{
							LOG.error("Problem connecting to Sabrix server to fetch TAX WSDL " + ex.getMessage() + " ", ex);
						}
						return service;
					}

					@Override
					public Service fallbackEvent()
					{
						return null;
					}

					@Override
					public Service defaultEvent()
					{
						return null;
					}
				}).execute();

		if (service == null)
		{
			LOG.error("Problem connecting to Sabrix server to fetch TAX WSDL");
			throw new Exception("Problem connecting to Sabrix server to fetch TAX WSDL", null);
		}
		return service;
	}

}
