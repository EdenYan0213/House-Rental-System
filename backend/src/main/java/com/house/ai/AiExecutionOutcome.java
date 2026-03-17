package com.house.ai;

public class AiExecutionOutcome {

  private final AiAssistantResponse response;
  private final String intent;
  private final String entitiesJson;
  private final String modelName;

  public AiExecutionOutcome(AiAssistantResponse response, String intent, String entitiesJson, String modelName) {
    this.response = response;
    this.intent = intent;
    this.entitiesJson = entitiesJson;
    this.modelName = modelName;
  }

  public AiAssistantResponse getResponse() {
    return response;
  }

  public String getIntent() {
    return intent;
  }

  public String getEntitiesJson() {
    return entitiesJson;
  }

  public String getModelName() {
    return modelName;
  }
}
