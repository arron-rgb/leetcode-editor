package com.shuzijun.leetcode.plugin.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.shuzijun.leetcode.plugin.model.*;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import com.shuzijun.leetcode.plugin.utils.*;
import com.shuzijun.leetcode.plugin.window.WindowFactory;

/**
 * @author shuzijun
 */
public class QuestionManager {

  private static Map<String, Question> dayMap = new LinkedHashMap<>();

  public static PageInfo<Question> getQuestionService(Project project, PageInfo pageInfo) {
    Boolean isPremium = false;
    if (HttpRequestUtils.isLogin()) {
      HttpRequest httpRequest = HttpRequest.post(URLUtils.getLeetcodeGraphql(), "application/json");
      if (URLUtils.isCn()) {
        httpRequest.setBody(
          "{\"query\":\"\\n    query globalData {\\n  userStatus {\\n    isSignedIn\\n    isPremium\\n    username\\n    realName\\n    avatar\\n    userSlug\\n    isAdmin\\n    useTranslation\\n    premiumExpiredAt\\n    isTranslator\\n    isSuperuser\\n    isPhoneVerified\\n    isVerified\\n  }\\n  jobsMyCompany {\\n    nameSlug\\n  }\\n  commonNojPermissionTypes\\n}\\n    \",\"variables\":{},\"operationName\":\"globalData\"}");
      } else {
        httpRequest.setBody(
          "{\"query\":\"\\n    query globalData {\\n  userStatus {\\n    userId\\n    isSignedIn\\n    isMockUser\\n    isPremium\\n    username\\n    avatar\\n    isAdmin\\n    isSuperuser\\n    permissions\\n    isTranslator\\n    notificationStatus {\\n      lastModified\\n      numUnread\\n    }\\n  }\\n}\\n    \",\"variables\":{},\"operationName\":\"globalData\"}");
      }
      httpRequest.addHeader("Accept", "application/json");
      HttpResponse response = HttpRequestUtils.executePost(httpRequest);
      if (response != null && response.getStatusCode() == 200) {
        JSONObject user = JSONObject.parseObject(response.getBody()).getJSONObject("data").getJSONObject("userStatus");
        isPremium = user.getBoolean("isPremium");
        ApplicationManager.getApplication().invokeAndWait(() -> {
          WindowFactory.updateTitle(project, user.getString("username"));
        });
      } else {
        LogUtils.LOG.error("Request userStatus  failed, status:" + response == null ? "" : response.getStatusCode());
      }
    }

    HttpRequest httpRequest = HttpRequest.post(URLUtils.getLeetcodeGraphql(), "application/json");
    if (URLUtils.isCn()) {
      httpRequest.setBody(
        "{\"query\":\"\\n    query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) {\\n  problemsetQuestionList(\\n    categorySlug: $categorySlug\\n    limit: $limit\\n    skip: $skip\\n    filters: $filters\\n  ) {\\n    hasMore\\n    total\\n    questions {\\n      acRate\\n      difficulty\\n      freqBar\\n      frontendQuestionId\\n      isFavor\\n      paidOnly\\n      solutionNum\\n      status\\n      title\\n      titleCn\\n      titleSlug\\n      topicTags {\\n        name\\n        nameTranslated\\n        id\\n        slug\\n      }\\n      extra {\\n        hasVideoSolution\\n        topCompanyTags {\\n          imgUrl\\n          slug\\n          numSubscribed\\n        }\\n      }\\n    }\\n  }\\n}\\n    \",\"variables\":{\"categorySlug\":\""
          + pageInfo.getCategorySlug() + "\",\"skip\":" + pageInfo.getSkip() + ",\"limit\":" + pageInfo.getPageSize()
          + ",\"filters\":" + pageInfo.getFilters().toString() + "},\"operationName\":\"problemsetQuestionList\"}");
    } else {
      httpRequest.setBody(
        "{\"query\":\"\\n    query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) {\\n  problemsetQuestionList: questionList(\\n    categorySlug: $categorySlug\\n    limit: $limit\\n    skip: $skip\\n    filters: $filters\\n  ) {\\n    total: totalNum\\n    questions: data {\\n      acRate\\n      difficulty\\n      freqBar\\n      frontendQuestionId: questionFrontendId\\n      isFavor\\n      paidOnly: isPaidOnly\\n      status\\n      title\\n      titleSlug\\n      topicTags {\\n        name\\n        id\\n        slug\\n      }\\n      hasSolution\\n      hasVideoSolution\\n    }\\n  }\\n}\\n    \",\"variables\":{\"categorySlug\":\""
          + pageInfo.getCategorySlug() + "\",\"skip\":" + pageInfo.getSkip() + ",\"limit\":" + pageInfo.getPageSize()
          + ",\"filters\":" + pageInfo.getFilters().toString() + "},\"operationName\":\"problemsetQuestionList\"}");
    }
    httpRequest.addHeader("Accept", "application/json");
    HttpResponse response = HttpRequestUtils.executePost(httpRequest);
    if (response != null && response.getStatusCode() == 200) {
      List questionList = parseQuestion(response.getBody(), isPremium);

      Question dayQuestion =
        dayMap.get(URLUtils.getLeetcodeHost() + new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
      if (dayQuestion == null) {
        dayQuestion = questionOfToday();
      }
      if (dayQuestion != null) {
        questionList.add(0, dayQuestion);
      }

      Integer total = JSONObject.parseObject(response.getBody()).getJSONObject("data")
        .getJSONObject("problemsetQuestionList").getInteger("total");
      pageInfo.setRowTotal(total);
      pageInfo.setRows(questionList);
    } else {
      LogUtils.LOG.error("Request question list failed, status:" + response == null ? "" : response.getStatusCode());
      throw new RuntimeException("Request question list failed");
    }

    return pageInfo;

  }

  public static List<Tag> getDifficulty() {

    List<String> keyList =
      Lists.newArrayList(Constant.DIFFICULTY_EASY, Constant.DIFFICULTY_MEDIUM, Constant.DIFFICULTY_HARD);
    List<Tag> difficultyList = Lists.newArrayList();
    for (String key : keyList) {
      Tag tag = new Tag();
      tag.setName(key);
      tag.setSlug(key.toUpperCase());
      difficultyList.add(tag);
    }
    return difficultyList;
  }

  public static List<Tag> getStatus() {
    List<String> keyList = Lists.newArrayList(Constant.STATUS_TODO, Constant.STATUS_SOLVED, Constant.STATUS_ATTEMPTED);

    List<Tag> statusList = Lists.newArrayList();
    for (String key : keyList) {
      Tag tag = new Tag();
      tag.setName(key);
      if (Constant.STATUS_TODO.equals(key)) {
        tag.setSlug("NOT_STARTED");
      } else if (Constant.STATUS_SOLVED.equals(key)) {
        tag.setSlug("AC");
      } else if (Constant.STATUS_ATTEMPTED.equals(key)) {
        tag.setSlug("TRIED");
      }
      statusList.add(tag);
    }
    return statusList;
  }

  public static List<Tag> getTags() {
    List<Tag> tags = new ArrayList<>();

    HttpRequest httpRequest = HttpRequest.get(URLUtils.getLeetcodeTags());
    HttpResponse response = HttpRequestUtils.executeGet(httpRequest);
    if (response != null && response.getStatusCode() == 200) {
      try {
        String body = response.getBody();
        tags = parseTag(body);
      } catch (Exception e1) {
        LogUtils.LOG.error("Request tags exception", e1);
      }
    } else {
      LogUtils.LOG.error("Request tags failed, status:" + response.getStatusCode() + "body:" + response.getBody());
    }

    return tags;
  }

  public static List<Tag> getLists() {
    List<Tag> tags = new ArrayList<>();

    HttpRequest httpRequest = HttpRequest.get(URLUtils.getLeetcodeFavorites());
    HttpResponse response = HttpRequestUtils.executeGet(httpRequest);
    if (response != null && response.getStatusCode() == 200) {
      try {
        String body = response.getBody();
        tags = parseList(body);
      } catch (Exception e1) {
        LogUtils.LOG.error("Request Lists exception", e1);
      }
    } else {
      LogUtils.LOG.error("Request Lists failed, status:" + response.getStatusCode() + "body:" + response.getBody());
    }
    return tags;
  }

  public static List<Tag> getCategory() {
    List<Tag> tags = new ArrayList<>();

    HttpRequest httpRequest = HttpRequest.get(URLUtils.getLeetcodeCardInfo());
    HttpResponse response = HttpRequestUtils.executeGet(httpRequest);
    if (response != null && response.getStatusCode() == 200) {
      try {
        String body = response.getBody();
        tags = parseCategory(body);
      } catch (Exception e1) {
        LogUtils.LOG.error("Request CardInfo exception", e1);
      }
    } else {
      LogUtils.LOG.error("Request CardInfo failed, status:" + response.getStatusCode() + "body:" + response.getBody());
    }
    return tags;
  }

  private static List<Question> parseQuestion(String str, Boolean isPremium) {

    List<Question> questionList = new ArrayList<Question>();

    if (StringUtils.isNotBlank(str)) {
      JSONObject jsonObject = JSONObject.parseObject(str).getJSONObject("data").getJSONObject("problemsetQuestionList");
      JSONArray jsonArray = jsonObject.getJSONArray("questions");
      for (int i = 0; i < jsonArray.size(); i++) {
        JSONObject object = jsonArray.getJSONObject(i);
        Question question = parseOneQuestion(object, isPremium);
        questionList.add(question);
      }
    }
    return questionList;
  }

  private static Question parseOneQuestion(JSONObject object, Boolean isPremium) {
    Question question = new Question(object.getString("title"));
    if (URLUtils.isCn() && !PersistentConfig.getInstance().getConfig().getEnglishContent()) {
      if (StringUtils.isNotBlank(object.getString("titleCn"))) {
        question.setTitle(object.getString("titleCn"));
      }
    }
    question.setLeaf(Boolean.TRUE);
    question.setFrontendQuestionId(object.getString("frontendQuestionId"));
    question.setAcceptance(object.getDouble("acRate"));
    try {
      if (object.getBoolean("paidOnly") && !isPremium) {
        question.setStatus("lock");
      } else {
        question.setStatus(object.get("status") == null ? "" : object.getString("status").toLowerCase());
      }
      if (object.containsKey("freqBar") && object.get("freqBar") != null) {
        question.setFrequency(object.getDouble("freqBar"));
      }
    } catch (Exception ee) {
      question.setStatus("");
    }
    question.setTitleSlug(object.getString("titleSlug"));
    question.setLevel(object.getString("difficulty"));
    try {
      if (object.containsKey("hasSolution")) {
        if (object.getBoolean("hasSolution")) {
          question.setArticleLive(Constant.ARTICLE_LIVE_ONE);
          question.setArticleSlug(object.getString("titleSlug"));
          question.setColumnArticles(1);
        } else {
          question.setArticleLive(Constant.ARTICLE_LIVE_NONE);
        }
      } else if (object.containsKey("solutionNum")) {
        question.setArticleLive(Constant.ARTICLE_LIVE_LIST);
        question.setColumnArticles(object.getInteger("solutionNum"));
      } else {
        question.setArticleLive(Constant.ARTICLE_LIVE_NONE);
      }
    } catch (Exception e) {
      LogUtils.LOG.error("Identify abnormal article", e);
      question.setArticleLive(Constant.ARTICLE_LIVE_NONE);
    }
    return question;
  }

  private static Question questionOfToday() {
    try {
      if (URLUtils.isCn()) {
        HttpRequest httpRequest = HttpRequest.post(URLUtils.getLeetcodeGraphql(), "application/json");
        httpRequest.setBody(
          "{\"query\":\"\\n    query questionOfToday {\\n  todayRecord {\\n    date\\n    userStatus\\n    question {\\n"
            + "      questionId\\n      frontendQuestionId: questionFrontendId\\n      difficulty\\n      title\\n      titleCn: translatedTitle\\n"
            + "      titleSlug\\n      paidOnly: isPaidOnly\\n      freqBar\\n      isFavor\\n      acRate\\n      status\\n      solutionNum\\n   "
            + "   hasVideoSolution\\n      topicTags {\\n        name\\n        nameTranslated: translatedName\\n        id\\n      }\\n      extra {\\n "
            + "       topCompanyTags {\\n          imgUrl\\n          slug\\n          numSubscribed\\n        }\\n      }\\n    }\\n    lastSubmission {\\n"
            + "      id\\n    }\\n  }\\n}\\n    \",\"variables\":{},\"operationName\":\"questionOfToday\"}");
        httpRequest.addHeader("Accept", "application/json");
        HttpResponse response = HttpRequestUtils.executePost(httpRequest);
        if (response == null || response.getStatusCode() != 200) {
          return null;
        } else {
          JSONObject jsonObject = JSONObject.parseObject(response.getBody()).getJSONObject("data")
            .getJSONArray("todayRecord").getJSONObject(0);
          Question question = parseOneQuestion(jsonObject.getJSONObject("question"), true);
          question.setStatus("day");
          dayMap.put(URLUtils.getLeetcodeHost() + jsonObject.getString("date"), question);
          return question;
        }
      } else {
        HttpRequest httpRequest = HttpRequest.post(URLUtils.getLeetcodeGraphql(), "application/json");
        httpRequest
          .setBody("{\"query\":\"\\n    query questionOfToday {\\n  activeDailyCodingChallengeQuestion {\\n    date\\n"
            + "    userStatus\\n    link\\n    question {\\n      acRate\\n      difficulty\\n      freqBar\\n      "
            + "frontendQuestionId: questionFrontendId\\n      isFavor\\n      paidOnly: isPaidOnly\\n      status\\n"
            + "      title\\n      titleSlug\\n      hasVideoSolution\\n      hasSolution\\n      topicTags {\\n "
            + "       name\\n        id\\n        slug\\n      }\\n    }\\n  }\\n}\\n    \",\"variables\":{},\"operationName\":\"questionOfToday\"}");
        httpRequest.addHeader("Accept", "application/json");
        HttpResponse response = HttpRequestUtils.executePost(httpRequest);
        if (response == null || response.getStatusCode() != 200) {
          return null;
        } else {
          JSONObject jsonObject = JSONObject.parseObject(response.getBody()).getJSONObject("data")
            .getJSONObject("activeDailyCodingChallengeQuestion");
          Question question = parseOneQuestion(jsonObject.getJSONObject("question"), true);
          question.setStatus("day");
          dayMap.put(URLUtils.getLeetcodeHost() + jsonObject.getString("date"), question);
          return question;
        }
      }
    } catch (Exception e) {
      return null;
    }
  }

  private static List<Tag> parseTag(String str) {
    List<Tag> tags = new ArrayList<Tag>();

    if (StringUtils.isNotBlank(str)) {

      JSONArray jsonArray = JSONObject.parseObject(str).getJSONArray("topics");
      for (int i = 0; i < jsonArray.size(); i++) {
        JSONObject object = jsonArray.getJSONObject(i);
        Tag tag = new Tag();
        tag.setSlug(object.getString("slug"));
        String name = object.getString(URLUtils.getTagName());
        if (StringUtils.isBlank(name)) {
          name = object.getString("name");
        }
        tag.setName(name);
        tags.add(tag);
      }
    }
    return tags;
  }

  private static List<Tag> parseCategory(String str) {
    List<Tag> tags = new ArrayList<Tag>();

    if (StringUtils.isNotBlank(str)) {

      JSONArray jsonArray = JSONArray.parseObject(str).getJSONObject("categories").getJSONArray("0");
      for (int i = 0; i < jsonArray.size(); i++) {
        JSONObject object = jsonArray.getJSONObject(i);
        Tag tag = new Tag();
        tag.setSlug(object.getString("slug"));
        tag.setType(URLUtils.getLeetcodeUrl() + "/api" + object.getString("url").replace("problemset", "problems"));
        tag.setName(object.getString("title"));
        tags.add(tag);
      }
    }
    return tags;
  }

  private static List<Tag> parseList(String str) {
    List<Tag> tags = new ArrayList<Tag>();

    if (StringUtils.isNotBlank(str)) {

      JSONArray jsonArray = JSONArray.parseArray(str);
      for (int i = 0; i < jsonArray.size(); i++) {
        JSONObject object = jsonArray.getJSONObject(i);
        Tag tag = new Tag();
        tag.setSlug(object.getString("id"));
        tag.setType(object.getString("type"));
        String name = object.getString("name");
        if (StringUtils.isBlank(name)) {
          name = object.getString("name");
        }
        tag.setName(name);
        JSONArray questionArray = object.getJSONArray("questions");
        for (int j = 0; j < questionArray.size(); j++) {
          tag.addQuestion(questionArray.getInteger(j).toString());
        }
        tags.add(tag);
      }
    }
    return tags;
  }

  public static boolean fillQuestion(Question question, CodeTypeEnum codeTypeEnum, Project project) {
    if (Constant.NODETYPE_ITEM.equals(question.getNodeType())) {
      ExploreManager.getItem(question);
      if (StringUtils.isBlank(question.getTitleSlug())) {
        MessageUtils.getInstance(project).showWarnMsg("info", PropertiesUtils.getInfo("response.restrict"));
        return false;
      } else {
        question.setNodeType(Constant.NODETYPE_DEF);
      }
    }
    if (StringUtils.isBlank(question.getQuestionId())) {
      return getQuestion(question, codeTypeEnum, project);
    }
    return true;
  }

  /**
   * queryQuestionData 获取题目数据
   *
   * @param question
   * @param codeTypeEnum
   * @param project
   * @return
   */
  public static boolean getQuestion(Question question, CodeTypeEnum codeTypeEnum, Project project) {
    try {
      HttpRequest httpRequest = HttpRequest.post(URLUtils.getLeetcodeGraphql(), "application/json");
      httpRequest.setBody("{\"operationName\":\"questionData\",\"variables\":{\"titleSlug\":\""
        + question.getTitleSlug()
        + "\"},\"query\":\"query questionData($titleSlug: String!) {\\n  question(titleSlug: $titleSlug) {\\n exampleTestcases\\n   questionId\\n    questionFrontendId\\n    boundTopicId\\n    title\\n    titleSlug\\n    content\\n    translatedTitle\\n    translatedContent\\n    isPaidOnly\\n    difficulty\\n    likes\\n    dislikes\\n    isLiked\\n    similarQuestions\\n    contributors {\\n      username\\n      profileUrl\\n      avatarUrl\\n      __typename\\n    }\\n    langToValidPlayground\\n    topicTags {\\n      name\\n      slug\\n      translatedName\\n      __typename\\n    }\\n    companyTagStats\\n    codeSnippets {\\n      lang\\n      langSlug\\n      code\\n      __typename\\n    }\\n    stats\\n    hints\\n    solution {\\n      id\\n      canSeeDetail\\n      __typename\\n    }\\n    status\\n    sampleTestCase\\n    metaData\\n    judgerAvailable\\n    judgeType\\n    mysqlSchemas\\n    enableRunCode\\n    enableTestMode\\n    envInfo\\n    __typename\\n  }\\n}\\n\"}");
      httpRequest.addHeader("Accept", "application/json");
      HttpResponse response = HttpRequestUtils.executePost(httpRequest);
      if (response.getStatusCode() == HttpStatus.SC_OK) {
        String body = response.getBody();
        JSONObject jsonObject = JSONObject.parseObject(body).getJSONObject("data").getJSONObject("question");
        question.setQuestionId(jsonObject.getString("questionId"));
        question.setContent(getContent(jsonObject));
        question.setTestCase(jsonObject.getString("sampleTestCase"));
        String exampleTestcases = jsonObject.getString("exampleTestcases");
        question.setExampleTestcases(exampleTestcases);

        JSONArray topicTags = jsonObject.getJSONArray("topicTags");
        for (int i = 0, n = topicTags.size(); i < n; i++) {
          Object topicTag = topicTags.get(i);
          if (topicTag instanceof JSONObject) {
            JSONObject tag = (JSONObject) topicTag;
            if (tag.getString("name") != null && "Design".equals(tag.getString("name"))) {
              question.setDesign(true);
            }
          }
          if (i == n - 1 && Objects.equals(!question.isDesign(), true)) {
            question.setDesign(false);
          }
        }

        JSONObject metaData = jsonObject.getJSONObject("metaData");
        question.setFunctionName(metaData.getString("name"));
        question.setParamTypes(metaData.getJSONArray("params").stream().map(t -> {
          String type = ((JSONObject) t).getString("type");
          type = typeMapping(type);
          return type;
        }).collect(Collectors.toList()));
        question.setReturnType(typeMapping(metaData.getJSONObject("return").getString("type")));
        question.setTitle(jsonObject.getString("title"));
        if (URLUtils.isCn() && !PersistentConfig.getInstance().getConfig().getEnglishContent()) {
          if (StringUtils.isNotBlank(jsonObject.getString("translatedTitle"))) {
            question.setTitle(jsonObject.getString("translatedTitle"));
          }
        }
        question.setLeaf(Boolean.TRUE);
        question.setFrontendQuestionId(jsonObject.getString("questionFrontendId"));
        question.setLevel(jsonObject.getString("difficulty"));
        if (URLUtils.isCn()) {
          question.setArticleLive(Constant.ARTICLE_LIVE_LIST);
        } else if (jsonObject.get("solution") != null) {
          question.setArticleLive(Constant.ARTICLE_LIVE_ONE);
          question.setArticleSlug(question.getTitleSlug());
        } else {
          question.setArticleLive(Constant.ARTICLE_LIVE_NONE);
        }


        JSONArray jsonArray = jsonObject.getJSONArray("codeSnippets");
        if (jsonArray == null) {
          question.setCode("Subscribe to unlock.");
          // todo add a prime account in configuration
          // if current account has no permission to view this question
          // use prime account to query submit
          // Question: How to save cookie && how to login?

        } else if (codeTypeEnum != null) {
          for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject object = jsonArray.getJSONObject(i);
            if (codeTypeEnum.getType().equals(object.getString("lang"))) {
              question.setLangSlug(object.getString("langSlug"));
              String sb = codeTypeEnum.getComment() + Constant.SUBMIT_REGION_BEGIN + "\n" +
                object.getString("code").replaceAll("\\n", "\n") + "\n" +
                codeTypeEnum.getComment() + Constant.SUBMIT_REGION_END + "\n";
              question.setCode(sb);
              break;
            }
            if (i == jsonArray.size() - 1) {
              question.setCode(
                codeTypeEnum.getComment() + "There is no code of " + codeTypeEnum.getType() + " type for this problem");
            }
          }
        }

        if (question.isDesign()) {
          question.setTitle(question.getTitle() + "Wrapper");
          String[] cases = exampleTestcases.split("\n");
//          String filePath = "/Users/arronshentu/Downloads/untitle";
          String filePath = PersistentConfig.getInstance().getTempFilePath() + "tmp"
            + VelocityUtils.convert(PersistentConfig.getInstance().getConfig().getCustomFileName(), question);
          FileUtils.saveFile(filePath, question.getCode());
          String s = InputUtils.generateTemplateCode(filePath, cases[0], cases[1], question);
          if (s.isEmpty()) {
            MessageUtils.getInstance(project).showWarnMsg("Design Code Error", "template code is empty");
          }
          question.setDesignCode(s);
          try {
            Files.deleteIfExists(Path.of(filePath));
          } catch (IOException ignored) {
            // do nothing
          }
        }
        return Boolean.TRUE;
      } else {
        MessageUtils.getInstance(project).showWarnMsg("error", PropertiesUtils.getInfo("response.code"));
      }

    } catch (Exception e) {
      LogUtils.LOG.error("获取代码失败", e);
      MessageUtils.getInstance(project).showWarnMsg("error", PropertiesUtils.getInfo("response.code"));
    }
    return Boolean.FALSE;
  }

  @NotNull
  private static String typeMapping(String type) {
    if (type.contains("list")) {
      type = type.replaceAll("list", "List");
      // basic type to boxed type
      type = type.replaceAll("integer", "Integer");
      type = type.replaceAll("string", "String");
      type = type.replaceAll("boolean", "Boolean");
      type = type.replaceAll("char", "Character");
      return type;
    }
    type = type.replaceAll("character", "char");
    type = type.replaceAll("string", "String");
    type = type.replaceAll("integer", "int");
    type = type.replaceAll("list", "List");
    return type;
  }

  private static String getContent(JSONObject jsonObject) {
    StringBuffer sb = new StringBuffer();
    sb.append(jsonObject.getString(URLUtils.getDescContent()));
//    Config config = PersistentConfig.getInstance().getConfig();
    Config config = new Config();
    config.setShowTopics(true);
    if (config.getShowTopics()) {
      JSONArray topicTagsArray = jsonObject.getJSONArray("topicTags");
      if (topicTagsArray != null && !topicTagsArray.isEmpty()) {
        sb.append("<div><div>Related Topics</div><div>");
        for (int i = 0; i < topicTagsArray.size(); i++) {
          JSONObject tag = topicTagsArray.getJSONObject(i);
          sb.append("<li>");
          if (StringUtils.isBlank(tag.getString("translatedName"))) {
            sb.append(tag.getString("name"));
          } else {
            sb.append(tag.getString("translatedName"));
          }
          sb.append("</li>");
        }
        sb.append("</div></div>");
        sb.append("<br>");
      }
    }
    sb.append("<div><li>\uD83D\uDC4D " + jsonObject.getInteger("likes") + "</li><li>\uD83D\uDC4E "
      + jsonObject.getInteger("dislikes") + "</li></div>");
    return sb.toString();
  }

  public static String pick() {
    String titleSlug = null;
    if (URLUtils.isCn()) {
      HttpRequest httpRequest = HttpRequest.get(URLUtils.getLeetcodeRandomOneQuestion());
      HttpResponse response = HttpRequestUtils.executeGet(httpRequest);
      if (response != null && response.getStatusCode() == 200) {
        String redirectsUrl = response.getUrl();
        String[] urls = redirectsUrl.split("/");
        titleSlug = urls[urls.length - 1];
      }
    } else {
      HttpRequest httpRequest = HttpRequest.post(URLUtils.getLeetcodeGraphql(), "application/json");
      httpRequest.setBody(
        "{\"query\":\"\\n    query randomQuestion($categorySlug: String, $filters: QuestionListFilterInput) {\\n  randomQuestion(categorySlug: $categorySlug, filters: $filters) {\\n    titleSlug\\n  }\\n}\\n    \",\"variables\":{\"categorySlug\":\"\",\"filters\":{}},\"operationName\":\"randomQuestion\"}");
      httpRequest.addHeader("Accept", "application/json");
      HttpResponse response = HttpRequestUtils.executePost(httpRequest);
      if (response != null && response.getStatusCode() == 200) {
        String body = response.getBody();
        titleSlug =
          JSONObject.parseObject(body).getJSONObject("data").getJSONObject("randomQuestion").getString("titleSlug");
      }
    }
    return titleSlug;
  }
}
