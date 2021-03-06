/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.twitter.inbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.messaging.Message;
import org.springframework.social.twitter.api.SearchMetadata;
import org.springframework.social.twitter.api.SearchOperations;
import org.springframework.social.twitter.api.SearchParameters;
import org.springframework.social.twitter.api.SearchResults;
import org.springframework.social.twitter.api.Tweet;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.social.twitter.api.impl.TwitterTemplate;


/**
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 */
public class SearchReceivingMessageSourceTests {

	private final Log logger = LogFactory.getLog(getClass());

	private static final String SEARCH_QUERY = "#springsource";

	@SuppressWarnings("unchecked")
	@Test
	@Ignore
	public void demoReceiveSearchResults() throws Exception {
		PropertiesFactoryBean pf = new PropertiesFactoryBean();
		pf.setLocation(new ClassPathResource("sample.properties"));
		pf.afterPropertiesSet();
		Properties prop = pf.getObject();
		TwitterTemplate template = new TwitterTemplate(prop.getProperty("z_oleg.oauth.consumerKey"),
				prop.getProperty("z_oleg.oauth.consumerSecret"),
				prop.getProperty("z_oleg.oauth.accessToken"),
				prop.getProperty("z_oleg.oauth.accessTokenSecret"));
		SearchReceivingMessageSource tSource = new SearchReceivingMessageSource(template, "foo");
		tSource.setQuery(SEARCH_QUERY);
		tSource.afterPropertiesSet();
		for (int i = 0; i < 50; i++) {
			Message<Tweet> message = tSource.receive();
			if (message != null) {
				Tweet tweet = message.getPayload();
				logger.info(tweet.getFromUser() + " - " + tweet.getText() + " - " + tweet.getCreatedAt());
			}
		}
	}

	/**
	 * Unit Test ensuring some basic initialization properties being set.
	 */
	@Test
	public void testSearchReceivingMessageSourceInit() {

		final SearchReceivingMessageSource messageSource =
				new SearchReceivingMessageSource(new TwitterTemplate("test"), "foo");
		messageSource.setComponentName("twitterSearchMessageSource");

		final Object metadataStore = TestUtils.getPropertyValue(messageSource, "metadataStore");
		final Object metadataKey = TestUtils.getPropertyValue(messageSource, "metadataKey");

		assertNull(metadataStore);
		assertNotNull(metadataKey);

		messageSource.setBeanFactory(mock(BeanFactory.class));
		messageSource.afterPropertiesSet();

		final Object metadataStoreInitialized = TestUtils.getPropertyValue(messageSource, "metadataStore");
		final Object metadataKeyInitialized = TestUtils.getPropertyValue(messageSource, "metadataKey");

		assertNotNull(metadataStoreInitialized);
		assertTrue(metadataStoreInitialized instanceof SimpleMetadataStore);
		assertNotNull(metadataKeyInitialized);
		assertEquals("foo", metadataKeyInitialized);

		final Twitter twitter = TestUtils.getPropertyValue(messageSource, "twitter", Twitter.class);

		assertFalse(twitter.isAuthorized());
		assertNotNull(twitter.userOperations());

	}

	/**
	 * This test ensures that when polling for a list of Tweets null is never returned.
	 * In case of no polling results, an empty list is returned instead.
	 */
	@Test
	public void testPollForTweetsNullResults() {

		final TwitterTemplate twitterTemplate = mock(TwitterTemplate.class);
		final SearchOperations so = mock(SearchOperations.class);

		given(twitterTemplate.searchOperations()).willReturn(so);
		given(twitterTemplate.searchOperations().search(SEARCH_QUERY, 20, 0, 0)).willReturn(null);

		final SearchReceivingMessageSource messageSource = new SearchReceivingMessageSource(twitterTemplate, "foo");
		messageSource.setQuery(SEARCH_QUERY);

		final String setQuery = TestUtils.getPropertyValue(messageSource, "query", String.class);

		assertEquals(SEARCH_QUERY, setQuery);
		assertEquals("twitter:search-inbound-channel-adapter", messageSource.getComponentType());

		final List<Tweet> tweets = messageSource.pollForTweets(0);

		assertNotNull(tweets);
		assertTrue(tweets.isEmpty());
	}

	/**
	 * Verify that a polling operation returns in fact 3 results.
	 */
	@Test
	public void testPollForTweetsThreeResults() {

		final TwitterTemplate twitterTemplate;

		final SearchOperations so = mock(SearchOperations.class);

		final List<Tweet> tweets = new ArrayList<Tweet>();

		tweets.add(mock(Tweet.class));
		tweets.add(mock(Tweet.class));
		tweets.add(mock(Tweet.class));

		final SearchResults results = new SearchResults(tweets, new SearchMetadata(111, 111));

		twitterTemplate = mock(TwitterTemplate.class);

		given(twitterTemplate.searchOperations()).willReturn(so);
		SearchParameters params = new SearchParameters(SEARCH_QUERY).count(20).sinceId(0);
		given(twitterTemplate.searchOperations().search(params)).willReturn(results);

		final SearchReceivingMessageSource messageSource = new SearchReceivingMessageSource(twitterTemplate, "foo");

		messageSource.setQuery(SEARCH_QUERY);

		final List<Tweet> tweetSearchResults = messageSource.pollForTweets(0);

		assertNotNull(tweetSearchResults);
		assertEquals(3, tweetSearchResults.size());
	}

}
