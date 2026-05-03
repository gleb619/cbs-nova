export interface BpmnRepository {
  fetchXml(code: string): Promise<string>;
}
