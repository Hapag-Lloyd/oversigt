/**
 * Oversigt API
 * This API provides access to all public operations of Oversigt.
 *
 * OpenAPI spec version: 1.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */
import { Action } from './action';


export interface RunStatistic {
    startTime?: Date;
    duration?: string;
    success?: boolean;
    automaticallyStarted?: boolean;
    message?: string;
    actions?: Array<Action>;
    throwableStackTrace?: string;
}
