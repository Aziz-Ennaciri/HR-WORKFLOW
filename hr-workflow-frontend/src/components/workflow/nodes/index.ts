import { EmailNode } from "./EmailNode";
import { GPTNode } from "./GPTNode";
import { DriveNode } from "./DriveNode";
import { ExcelNode } from "./ExcelNode";

export const nodeTypes = {
  email: EmailNode,
  gpt: GPTNode,
  drive: DriveNode,
  excel: ExcelNode,
};
