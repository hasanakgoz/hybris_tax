/*
 * [y] hybris Platform
 *
 * Copyright (c) 2000-2017 SAP SE
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * Hybris ("Confidential Information"). You shall not disclose such
 * Confidential Information and shall use it only in accordance with the
 * terms of the license agreement you entered into with SAP Hybris.
 */
package com.hybris.tax.custom.core;

import de.hybris.platform.core.model.c2l.CountryModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.integration.commons.hystrix.HystrixExecutable;
import de.hybris.platform.integration.commons.hystrix.OndemandHystrixCommandConfiguration;
import de.hybris.platform.integration.commons.hystrix.OndemandHystrixCommandFactory;
import de.hybris.platform.util.Config;
import de.hybris.platform.util.DiscountValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.ws.Binding;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.IndataInvoiceType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.IndataLineType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.IndataType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataErrorType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataInvoiceType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataRequestStatusType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.QuantitiesType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.QuantityType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationFault_Exception;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationRequest;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationResponse;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.TaxCalculationService;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.VersionType;
import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.ZoneAddressType;


/**
 *
 */
public class SabrixCall
{
	private static final Logger LOG = Logger.getLogger(SabrixCall.class);
	private OndemandHystrixCommandFactory ondemandHystrixCommandFactory;
	private OndemandHystrixCommandConfiguration hystrixCommandConfig;
	private static ObjectPool<ProxyWrapper<TaxCalculationService>> pool = null;
	private Service service = null;

	public OutdataInvoiceType taxCalculation(final AbstractOrderModel cart) throws Exception
	{
		LOG.warn("***************TAX CALCULATION BEGINS******************");
		/* Create the service instance */
		boolean isSuccess = false;
		final OutdataInvoiceType outDataInvoice = new OutdataInvoiceType();
		ProxyWrapper<TaxCalculationService> wrapper = null;
		try
		{
			final String url = Config.getString("tax.connection.url", "") + "?wsdl";
			Integer.parseInt(Config.getString("tax.connection.retry", "1"));
			wrapper = fetchServicePort(url);
			final TaxCalculationService servicePort = wrapper.getItem();
			final Binding binding = fetchBindingObject(url, servicePort);
			final TaxCalculationRequest taxRequest = new TaxCalculationRequest();
			final IndataType inData = new IndataType();
			final IndataInvoiceType invoiceType = new IndataInvoiceType();
			final List<IndataInvoiceType> multipleInvoiceType = setUpIndataAndInvoiceTypeData(cart, inData, invoiceType);
			final UserModel userModel = cart.getUser();
			IndataLineType lineItem;
			AbstractOrderEntryModel orderEntry;
			if (null != userModel)
			{
				invoiceType.setCUSTOMERNAME(userModel.getName());
				invoiceType.setCUSTOMERNUMBER(userModel.getUid());
				populateShipToAddressInInvoiceType(cart, invoiceType, userModel);
			}

			String invoiceNumber = cart.getCode().replaceFirst("^0*", "");
			invoiceNumber = invoiceNumber + "-" + new SimpleDateFormat("yyyyMMdd").format(cart.getCreationtime());
			invoiceType.setINVOICENUMBER(invoiceNumber);

			populateBillToAddressForInvoiceType(cart, invoiceType);

			final List<IndataLineType> multipleLineItems = invoiceType.getLINE();
			final List<AbstractOrderEntryModel> entries = cart.getEntries();


			final List<Integer> entriesNoList = new ArrayList<Integer>();
			if (null != entries && entries.size() > 0)
			{
				java.lang.System.currentTimeMillis();
				for (int i = 0; i < entries.size(); i++)
				{
					orderEntry = entries.get(i);
					final ProductModel product = orderEntry.getProduct();
					calculateTotalDiscount(orderEntry);
					lineItem = new IndataLineType();
					lineItem.setID(String.valueOf((orderEntry.getEntryNumber().intValue() + 1)));
					entriesNoList.add(orderEntry.getEntryNumber());
					lineItem.setLINENUMBER(new BigDecimal(orderEntry.getEntryNumber().intValue() + 1));
					lineItem.setPRODUCTCODE(product.getCode());
					lineItem.setDESCRIPTION(product.getDescription());
					lineItem.setDESCRIPTION(product.getName());
					lineItem.getUSERELEMENT();
					lineItem.setGROSSAMOUNT(String.valueOf(orderEntry.getTotalPrice().doubleValue()));
					populateQuantityForLineItem(lineItem, orderEntry);
					multipleLineItems.add(lineItem);
				}
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
			final TaxCalculationResponse taxResponse = calculateTax(taxRequest, servicePort);
			final OutdataType outData = taxResponse.getOUTDATA();
			if (null != outData)
			{
				final OutdataRequestStatusType requestStatus = outData.getREQUESTSTATUS();
				if (requestStatus.isISSUCCESS())
				{
					final List<OutdataInvoiceType> outDataInvoiceType = outData.getINVOICE();
					if (!outDataInvoiceType.isEmpty() && null != outDataInvoiceType.get(0))
					{
						isSuccess = true;
					}
				}
				else if (null != requestStatus.getERROR())
				{
					final List<OutdataErrorType> taxErrors = requestStatus.getERROR();
					if (!taxErrors.isEmpty())
					{
						isSuccess = false;
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
					LOG.error("Failed to return pool object", e);
				}
			}
		}
		return isSuccess ? outDataInvoice : null;
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

	private void populateShiptoAddress(final AddressModel address, final IndataInvoiceType invoiceType)
	{
		final ZoneAddressType shipTo = new ZoneAddressType();
		final CountryModel country = address.getCountry();

		if (null != country)
		{
			shipTo.setCOUNTRY(country.getIsocode());
			if (country.getIsocode().equalsIgnoreCase("US"))
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

	private List<IndataInvoiceType> setUpIndataAndInvoiceTypeData(final AbstractOrderModel cart, final IndataType inData,
			final IndataInvoiceType invoiceType)
	{
		final VersionType version = inData.getVersion();
		inData.setVersion(version.G);
		final List<IndataInvoiceType> multipleInvoiceType = inData.getINVOICE();
		invoiceType.setCALLINGSYSTEMNUMBER(Config.getString("tax.calling.system.number", ""));
		/*
		 * invoiceType.setEXTERNALCOMPANYID(DoterraIntegrationConstants.EXTERNAL_COMPANY_ID);
		 * invoiceType.setHOSTSYSTEM(DoterraIntegrationConstants.HOST_SYSTEM);
		 * invoiceType.setCALCULATIONDIRECTION(DoterraIntegrationConstants.CALCULATION_DIRECTION);
		 * invoiceType.setCOMPANYROLE(DoterraIntegrationConstants.COMPANY_ROLE);
		 * invoiceType.setCURRENCYCODE(cart.getCurrency().getIsocode());
		 * invoiceType.setINVOICEDATE(DoterraIntegrationConstants.DATE_FORMATTER_TIME.format(new Date()));//current date
		 * in 'yyyy-MM-dd HH:mm:ss' format invoiceType.setISAUDITED(DoterraIntegrationConstants.IS_AUDITED);
		 * invoiceType.setTRANSACTIONTYPE(DoterraIntegrationConstants.TRANSACTION_TYPE);
		 * invoiceType.setISREVERSED(DoterraIntegrationConstants.IS_AUDITED);
		 * invoiceType.setISREPORTED(DoterraIntegrationConstants.IS_AUDITED);
		 */
		return multipleInvoiceType;
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
					pool = new GenericObjectPool<ProxyWrapper<TaxCalculationService>>(new CustomPoolFactory(service), poolConfig);
				}
			}
		}

		ProxyWrapper<TaxCalculationService> wrapper = null;
		wrapper = pool.borrowObject();
		return wrapper;
	}

	protected OndemandHystrixCommandFactory getOndemandHystrixCommandFactory()
	{
		return ondemandHystrixCommandFactory;
	}

	protected OndemandHystrixCommandConfiguration getHystrixCommandConfig()
	{
		return hystrixCommandConfig;
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


	private Binding fetchBindingObject(final String url, final TaxCalculationService servicePort)
	{
		final BindingProvider bp = (BindingProvider) servicePort;

		java.lang.System.currentTimeMillis();
		java.lang.System.currentTimeMillis();

		//Set timeout until the response is received
		final long p1 = java.lang.System.currentTimeMillis();
		//bp.getRequestContext().put("javax.xml.ws.client.receiveTimeout", connectionTimeout);
		bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
		final long p2 = java.lang.System.currentTimeMillis();
		LOG.warn("DoterraTaxServiceImpl: Time Took To Set Timeout Until A Response Is Received" + (int) ((p2 - p1) / 1000) % 60);
		final Binding binding = bp.getBinding();
		return binding;
	}

	private int populateDiscountsInLineItems(final List<IndataLineType> multipleLineItems, int entriesSize,
			final double otherDiscounts)
	{
		IndataLineType lineItem;
		if (otherDiscounts != 0.0)
		{
			lineItem = new IndataLineType();
			entriesSize = entriesSize + 1;
			lineItem.setID(String.valueOf(entriesSize));
			lineItem.setLINENUMBER(new BigDecimal(entriesSize));
			lineItem.setPRODUCTCODE("DISC");
			lineItem.setGROSSAMOUNT("-" + otherDiscounts);
			multipleLineItems.add(lineItem);
		}
		return entriesSize;
	}

	private void populateDeliveryCostInLineItems(final AbstractOrderModel cart, final List<IndataLineType> multipleLineItems,
			int entriesSize, final double shippingDiscount)
	{
		IndataLineType lineItem;
		if (null != cart.getDeliveryCost() && cart.getDeliveryCost().doubleValue() != 0.0)
		{
			lineItem = new IndataLineType();
			entriesSize = entriesSize + 1;
			lineItem.setID(String.valueOf(entriesSize));
			lineItem.setLINENUMBER(new BigDecimal(entriesSize));
			lineItem.setPRODUCTCODE("FREIGHT");
			double deliveryCost = 0.0;
			if (shippingDiscount != 0.0)
			{
				deliveryCost = cart.getDeliveryCost().doubleValue() - shippingDiscount;
			}
			else
			{
				deliveryCost = cart.getDeliveryCost().doubleValue();
			}
			lineItem.setGROSSAMOUNT(String.valueOf(deliveryCost));
			multipleLineItems.add(lineItem);
		}
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
}
