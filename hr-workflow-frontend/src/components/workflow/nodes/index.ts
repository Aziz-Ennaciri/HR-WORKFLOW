import { EmailNode } from "./EmailNode";
import { GPTNode } from "./GPTNode";
import { DriveNode } from "./DriveNode";
import { ExcelNode } from "./ExcelNode";
import { ApprovalNode } from "./ApprovalNode";

export const nodeTypes = {
  email: EmailNode,
  gpt: GPTNode,
  drive: DriveNode,
  excel: ExcelNode,
  approval: ApprovalNode,
};
