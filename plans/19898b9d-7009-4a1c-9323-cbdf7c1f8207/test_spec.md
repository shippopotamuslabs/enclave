# Testing Specification for Non-PKCE X Platform Tools

This document defines the formal behavioral testing specification for the four new X (Twitter) public-data tools using Gherkin syntax.

---

## Scenarios

### SCENARIO_GET_THREAD_SUCCESS
```gherkin
Given a configured X Search repository with a valid Bearer token
When x_get_thread is executed with:
  """
  {
    "tweet_url": "https://x.com/jack/status/20"
  }
  """
And the target tweet fetched by ID "20" returns:
  """
  {
    "data": [
      {
        "id": "20",
        "text": "just setting up my twttr",
        "conversation_id": "20",
        "author_id": "12",
        "created_at": "2006-03-21T20:50:14.000Z"
      }
    ],
    "includes": {
      "users": [
        {
          "id": "12",
          "name": "jack",
          "username": "jack"
        }
      ]
    }
  }
  """
And the recent search for replies matching "conversation_id:20 -is:retweet -is:nullcast" returns:
  """
  {
    "data": [
      {
        "id": "25",
        "text": "cool project!",
        "conversation_id": "20",
        "author_id": "15",
        "created_at": "2006-03-21T20:55:00.000Z"
      }
    ],
    "includes": {
      "users": [
        {
          "id": "15",
          "name": "biz",
          "username": "biz"
        }
      ]
    }
  }
  """
Then the tool execution result JSON must exactly match the literal:
  """
  {
    "data": [
      {
        "id": "20",
        "text": "just setting up my twttr",
        "author_id": "12",
        "user_name": "jack",
        "url": "https://x.com/jack/status/20",
        "created_at": "2006-03-21T20:50:14.000Z",
        "metrics": {
          "retweet_count": 0,
          "reply_count": 0,
          "like_count": 0,
          "quote_count": 0,
          "bookmark_count": 0,
          "impression_count": 0
        },
        "media": null,
        "urls": null,
        "type": null,
        "impact_score": 0.0
      },
      {
        "id": "25",
        "text": "cool project!",
        "author_id": "15",
        "user_name": "biz",
        "url": "https://x.com/biz/status/25",
        "created_at": "2006-03-21T20:55:00.000Z",
        "metrics": {
          "retweet_count": 0,
          "reply_count": 0,
          "like_count": 0,
          "quote_count": 0,
          "bookmark_count": 0,
          "impression_count": 0
        },
        "media": null,
        "urls": null,
        "type": null,
        "impact_score": 0.0
      }
    ],
    "warning": "X recent search API is limited to the last 7 days. Replies older than 7 days or matching spam filters may not be returned."
  }
  """
```

### SCENARIO_GET_THREAD_AS_REPLY
```gherkin
Given a configured X Search repository with a valid Bearer token
When x_get_thread is executed with:
  """
  {
    "tweet_url": "https://x.com/biz/status/25"
  }
  """
And the target tweet fetched by ID "25" returns a reply:
  """
  {
    "data": [
      {
        "id": "25",
        "text": "cool project!",
        "conversation_id": "20",
        "author_id": "15",
        "created_at": "2006-03-21T20:55:00.000Z"
      }
    ],
    "includes": {
      "users": [
        {
          "id": "15",
          "name": "biz",
          "username": "biz"
        }
      ]
    }
  }
  """
And the root tweet fetched by conversation ID "20" returns:
  """
  {
    "data": [
      {
        "id": "20",
        "text": "just setting up my twttr",
        "conversation_id": "20",
        "author_id": "12",
        "created_at": "2006-03-21T20:50:14.000Z"
      }
    ],
    "includes": {
      "users": [
        {
          "id": "12",
          "name": "jack",
          "username": "jack"
        }
      ]
    }
  }
  """
And the recent search for replies matching "conversation_id:20 -is:retweet -is:nullcast" returns:
  """
  {
    "data": [
      {
        "id": "25",
        "text": "cool project!",
        "conversation_id": "20",
        "author_id": "15",
        "created_at": "2006-03-21T20:55:00.000Z"
      }
    ],
    "includes": {
      "users": [
        {
          "id": "15",
          "name": "biz",
          "username": "biz"
        }
      ]
    }
  }
  """
Then the tool execution result JSON must exactly match the literal:
  """
  {
    "data": [
      {
        "id": "20",
        "text": "just setting up my twttr",
        "author_id": "12",
        "user_name": "jack",
        "url": "https://x.com/jack/status/20",
        "created_at": "2006-03-21T20:50:14.000Z",
        "metrics": {
          "retweet_count": 0,
          "reply_count": 0,
          "like_count": 0,
          "quote_count": 0,
          "bookmark_count": 0,
          "impression_count": 0
        },
        "media": null,
        "urls": null,
        "type": null,
        "impact_score": 0.0
      },
      {
        "id": "25",
        "text": "cool project!",
        "author_id": "15",
        "user_name": "biz",
        "url": "https://x.com/biz/status/25",
        "created_at": "2006-03-21T20:55:00.000Z",
        "metrics": {
          "retweet_count": 0,
          "reply_count": 0,
          "like_count": 0,
          "quote_count": 0,
          "bookmark_count": 0,
          "impression_count": 0
        },
        "media": null,
        "urls": null,
        "type": null,
        "impact_score": 0.0
      }
    ],
    "warning": "X recent search API is limited to the last 7 days. Replies older than 7 days or matching spam filters may not be returned."
  }
  """
```

### SCENARIO_GET_THREAD_7DAY_WARNING
```gherkin
Given a configured X Search repository with a valid Bearer token
When x_get_thread is executed on a tweet where the root tweet is older than 7 days
Then the thread is assembled from target and root tweets
And the "warning" field in the output JSON must exactly be:
  """
  "X recent search API is limited to the last 7 days. Replies older than 7 days or matching spam filters may not be returned."
  """
```

### SCENARIO_TRENDS_SUCCESS
```gherkin
Given a configured X Search repository with a valid Bearer token
When x_trending is executed with:
  """
  {
    "location": "worldwide"
  }
  """
And the GET request to /2/trends/by/woeid/1 returns:
  """
  {
    "data": [
      {
        "trend_name": "#GoldStandard",
        "tweet_count": 12345
      }
    ]
  }
  """
Then the tool execution result JSON must exactly match the literal:
  """
  {
    "data": [
      {
        "trend_name": "#GoldStandard",
        "tweet_count": 12345
      }
    ]
  }
  """
```


### SCENARIO_USER_TWEETS_SUCCESS
```gherkin
Given a configured X Search repository with a valid Bearer token
When x_user_tweets is executed with:
  """
  {
    "username": "@elonmusk",
    "search_text": null
  }
  """
And the query is sanitized to "from:elonmusk"
And the search results return a list of 2 tweets and next_token "tok123"
Then the tool execution result JSON must exactly match the literal:
  """
  {
    "data": [
      {
        "id": "100",
        "text": "first tweet",
        "author_id": "elon",
        "user_name": "Elon Musk",
        "url": "https://x.com/elonmusk/status/100",
        "created_at": "2026-05-24T10:00:00.000Z",
        "metrics": {
          "retweet_count": 0,
          "reply_count": 0,
          "like_count": 0,
          "quote_count": 0,
          "bookmark_count": 0,
          "impression_count": 0
        },
        "media": null,
        "urls": null,
        "type": null,
        "impact_score": 0.0
      },
      {
        "id": "101",
        "text": "second tweet",
        "author_id": "elon",
        "user_name": "Elon Musk",
        "url": "https://x.com/elonmusk/status/101",
        "created_at": "2026-05-24T10:05:00.000Z",
        "metrics": {
          "retweet_count": 0,
          "reply_count": 0,
          "like_count": 0,
          "quote_count": 0,
          "bookmark_count": 0,
          "impression_count": 0
        },
        "media": null,
        "urls": null,
        "type": null,
        "impact_score": 0.0
      }
    ],
    "next_token": "tok123"
  }
  """
```

### SCENARIO_USER_TWEETS_PAGINATION
```gherkin
Given a configured X Search repository with a valid Bearer token
When x_user_tweets is executed with:
  """
  {
    "username": "elonmusk",
    "search_text": "tesla",
    "next_token": "tok123"
  }
  """
Then the query passed to the HTTP request must contain "query=from%3Aelonmusk%20tesla" and "next_token=tok123"
```

### SCENARIO_MENTION_SEARCH_SUCCESS
```gherkin
Given a configured X Search repository with a valid Bearer token
When x_mention_search is executed with:
  """
  {
    "username": "@elonmusk",
    "search_text": "spacex",
    "next_token": null
  }
  """
Then the query passed to the HTTP request must contain "query=%40elonmusk%20spacex"
```

### SCENARIO_MUTATION_DEFENSE_USER_TWEETS
```gherkin
Given a developer attempts a mock or stubbed implementation of x_user_tweets
When x_user_tweets is executed with:
  """
  {
    "username": "nasa",
    "search_text": "mars",
    "next_token": "nasa_token"
  }
  """
Then the request query built must be "from:nasa mars" (sanitized and built dynamically)
And the HTTP call must target "https://api.x.com/2/tweets/search/recent" with query param "query=from%3Anasa%20mars" and "next_token=nasa_token"
And the response must parse results with "user_name" matching "NASA" and "next_token" matching "next_nasa_token"
And the execution will FAIL if any hardcoded "elonmusk" or empty results are returned.
```

### SCENARIO_PREPARER_ELIGIBLE_TOOLS
```gherkin
Given ChatInferenceRequestPreparer is invoked
When xSearchConfigured is true
Then the eligibleTools list must include ToolDefinition.X_GET_THREAD, ToolDefinition.X_TRENDING, ToolDefinition.X_USER_TWEETS, and ToolDefinition.X_MENTION_SEARCH
And none of these four tools may be in the preloaded tools set (they must remain DISCLOSE_ON_DEMAND)
```

### SCENARIO_COMPOSITE_EXECUTOR_ROUTING
```gherkin
Given CompositeToolExecutor receives a ToolCallRequest
When toolName is "x_get_thread"
Then it must route execution to XGetThreadToolExecutor
When toolName is "x_trending"
Then it must route execution to XTrendingToolExecutor
When toolName is "x_user_tweets"
Then it must route execution to XUserTweetsToolExecutor
When toolName is "x_mention_search"
Then it must route execution to XMentionSearchToolExecutor
```
