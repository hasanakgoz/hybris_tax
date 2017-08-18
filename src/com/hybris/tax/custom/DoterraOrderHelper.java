/**
 *
 */
package com.hybris.doterra.core.util;

import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.config.ConfigurationService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.hybris.doterra.core.constants.DoterraCoreConstants;
import com.hybris.doterra.core.enums.DoterraOrderTypeEnum;
import com.hybris.doterra.core.enums.OrderLineEntryStatus;

/**
 * @author varujain
 *
 */
public class DoterraOrderHelper {
	private static final Logger LOG = Logger.getLogger(DoterraOrderHelper.class);

	@Resource(name = "configurationService")
	private ConfigurationService configurationService;

	/**
	 * Parse tax values in Map, so that it can be used easily.
	 *
	 * @param str
	 * @return
	 */
	public Map<String, String> parseTaxValues(final String str) {
		final String[] tokens = str.split(";|:");
		final Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i < tokens.length - 1;) {
			map.put(tokens[i++], tokens[i++]);
		}
		return map;
	}

	/**
	 * return a map of tracking details for List<AbstractOrderEntryModel>
	 *
	 * @param sourceEntries
	 * @return
	 */
	public Map<String, Set<String>> buildTrackingDetailsMap(final List<AbstractOrderEntryModel> sourceEntries) {
		final Map<String, Set<String>> trackingDetailsMap = new HashMap<String, Set<String>>();
		final Set<String> upsTrackingIdSet = new HashSet<String>();
		final Set<String> uspsTrackingIdSet = new HashSet<String>();
		final Set<String> fedexTrackingIdSet = new HashSet<String>();

		for (final AbstractOrderEntryModel entry : sourceEntries) {
			if (null != entry.getDoterraOrderEntryTrackingID()
					&& entry.getDoterraOrderEntryStatus().equals(OrderLineEntryStatus.SHIPPED)
					&& null != entry.getShippingMethod()) {
				final String[] trackIds = entry.getDoterraOrderEntryTrackingID().split("//");
				final String[] carrier = entry.getShippingMethod().split("//");
				for (int i = 0; i < carrier.length; i++) {
					if ((carrier[i].toUpperCase()).contains(DoterraCoreConstants.UPS_CARRIER)) {
						final String trackingId = trackIds[i];
						final String[] setTrackId = trackingId.split("/");
						for (final String id : setTrackId) {
							upsTrackingIdSet.add(id);
						}
						trackingDetailsMap.put(DoterraCoreConstants.UPS_CARRIER, upsTrackingIdSet);
					}
					if ((carrier[i].toUpperCase()).contains(DoterraCoreConstants.USPS_CARRIER)) {
						final String trackingId = trackIds[i];
						final String[] setTrackId = trackingId.split("/");
						for (final String id : setTrackId) {
							uspsTrackingIdSet.add(id);
						}
						trackingDetailsMap.put(DoterraCoreConstants.USPS_CARRIER, uspsTrackingIdSet);
					}
					if ((carrier[i].toUpperCase()).contains(DoterraCoreConstants.FEDEX_CARRIER)) {
						final String trackingId = trackIds[i];
						final String[] setTrackId = trackingId.split("/");
						for (final String id : setTrackId) {
							fedexTrackingIdSet.add(id);
						}
						trackingDetailsMap.put(DoterraCoreConstants.FEDEX_CARRIER, fedexTrackingIdSet);
					}
				}
			}
		}
		return trackingDetailsMap;
	}

	/**
	 * @param trackingDetailsMap
	 * @return List
	 */
	public List<String> checkShippingPattern(final Map<String, Set<String>> trackingDetailsMap) {
		final Set<String> carrierSet = trackingDetailsMap.keySet();
		final List<String> urlList = new ArrayList<String>();

		if (CollectionUtils.isNotEmpty(carrierSet)) {
			for (final String carrier : carrierSet) {
				final Set<String> trackingNumberSet = trackingDetailsMap.get(carrier);
				final String url = this.generateURL(carrier, trackingNumberSet);
				urlList.add(url);
			}
		}
		return urlList;
	}

	/**
	 * @param carrier
	 * @param trackingNumberSet
	 * @return String
	 */
	private String generateURL(final String carrier, final Set<String> trackingNumberSet) {
		String url = DoterraCoreConstants.EMPTY_STRING;
		for (final String id : trackingNumberSet) {
			if (null != carrier && carrier.toUpperCase().contains(DoterraCoreConstants.UPS_CARRIER)) {
				if (StringUtils.isEmpty(url)) {
					url = configurationService.getConfiguration().getString(DoterraCoreConstants.SHIPPING_UPS) + id;
				} else {
					url = url + "%0A" + id;
				}
			}
			if (null != carrier && carrier.toUpperCase().contains(DoterraCoreConstants.USPS_CARRIER)) {
				if (StringUtils.isEmpty(url)) {
					url = configurationService.getConfiguration().getString(DoterraCoreConstants.SHIPPING_USPS) + id;
				} else {
					url = url + "," + id;
				}
			}
			if (null != carrier && carrier.toUpperCase().contains(DoterraCoreConstants.FEDEX_CARRIER)) {
				if (StringUtils.isEmpty(url)) {
					url = configurationService.getConfiguration().getString(DoterraCoreConstants.SHIPPING_FEDEX) + id;
				} else {
					url = url + "," + id;
				}
			}
		}
		return url;
	}

	public void populateLRPTemplateForOrdersCreated(final CartModel lrpCartModel, final OrderModel order) {
		if (null != order.getOrderType() && (order.getOrderType().equals(DoterraOrderTypeEnum.LRP)
				|| order.getOrderType().equals(DoterraOrderTypeEnum.LRPNOW))) {
			final List<OrderModel> ordersForTemplates = lrpCartModel.getOrder();
			final List<OrderModel> newList = new ArrayList<>();
			if (CollectionUtils.isNotEmpty(ordersForTemplates)) {
				newList.addAll(ordersForTemplates);
				newList.add(order);
				lrpCartModel.setOrder(newList);
			} else {
				newList.add(order);
				lrpCartModel.setOrder(newList);
			}
			final Date lrpLastProcessedTime = lrpCartModel.getLrpLastProcessedTime();
			final Calendar date = Calendar.getInstance();
			final int currentMonth = date.get(Calendar.MONTH);
			if (lrpLastProcessedTime != null) {
				date.setTime(lrpLastProcessedTime);
				final int lastProcessedMonth = date.get(Calendar.MONTH);
				if (currentMonth > lastProcessedMonth && lrpCartModel.getIsLrpProcessedThisMonth()) {
					lrpCartModel.setIsLrpProcessedThisMonth(false);
				}
			}
			lrpCartModel.setLrpLastProcessedTime(order.getCreationtime());
			lrpCartModel.setLastOrderNumber(order.getCode());
			lrpCartModel.setLastRunPeriod(order.getCreationtime());
		}
	}

	public String checkTrackingUrlForNotification(final OrderModel order) {
		String formattedTrackingId = StringUtils.EMPTY;
		final Map<String, Set<String>> trackingDetailsMap = buildTrackingDetailsMap(order.getEntries());
		if (MapUtils.isNotEmpty(trackingDetailsMap)) {
			List<String> trackIds = new ArrayList<String>();
			trackIds = checkShippingPattern(trackingDetailsMap);
			if (CollectionUtils.isNotEmpty(trackIds) && trackIds.size() == 1) {
				formattedTrackingId = trackIds.get(0);
			} else if (trackIds.size() > 1) {
				String baseUrl = trackIds.get(0);
				final Set<String> multiTrackId = new HashSet<String>();
				final Set<String> keySet = trackingDetailsMap.keySet();
				final List<String> listKeys = new LinkedList<String>(keySet);
				for (int i = 1; i < listKeys.size(); i++) {
					final String key = listKeys.get(i);
					multiTrackId.addAll(trackingDetailsMap.get(key));
				}
				if (CollectionUtils.isNotEmpty(multiTrackId)) {
					if (baseUrl.contains("ups")) {
						for (final String id : multiTrackId) {
							baseUrl = baseUrl + "%0A" + id;
						}
					} else {
						for (final String id : multiTrackId) {
							baseUrl = baseUrl + "," + id;
						}
					}
				}
				formattedTrackingId = baseUrl;
			}
		}
		return formattedTrackingId;
	}
}
