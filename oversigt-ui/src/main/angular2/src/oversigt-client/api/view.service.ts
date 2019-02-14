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
/* tslint:disable:no-unused-variable member-ordering */

import { Inject, Injectable, Optional }                      from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams,
         HttpResponse, HttpEvent }                           from '@angular/common/http';
import { CustomHttpUrlEncodingCodec }                        from '../encoder';

import { Observable }                                        from 'rxjs';


import { BASE_PATH, COLLECTION_FORMATS }                     from '../variables';
import { Configuration }                                     from '../configuration';


@Injectable()
export class ViewService {

    protected basePath = 'http://localhost/api/v1';
    public defaultHeaders = new HttpHeaders();
    public configuration = new Configuration();

    constructor(protected httpClient: HttpClient, @Optional()@Inject(BASE_PATH) basePath: string, @Optional() configuration: Configuration) {
        if (basePath) {
            this.basePath = basePath;
        }
        if (configuration) {
            this.configuration = configuration;
            this.basePath = basePath || configuration.basePath || this.basePath;
        }
    }

    /**
     * @param consumes string[] mime-types
     * @return true: consumes contains 'multipart/form-data', false: otherwise
     */
    private canConsumeForm(consumes: string[]): boolean {
        const form = 'multipart/form-data';
        for (const consume of consumes) {
            if (form === consume) {
                return true;
            }
        }
        return false;
    }


    /**
     * Read the CSS data of the given widget
     * 
     * @param viewId 
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public readCss(viewId: string, observe?: 'body', reportProgress?: boolean): Observable<any>;
    public readCss(viewId: string, observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<any>>;
    public readCss(viewId: string, observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<any>>;
    public readCss(viewId: string, observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        if (viewId === null || viewId === undefined) {
            throw new Error('Required parameter viewId was null or undefined when calling readCss.');
        }

        let headers = this.defaultHeaders;

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'text/css'
        ];
        const httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set('Accept', httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        const consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<any>(`${this.basePath}/views/${encodeURIComponent(String(viewId))}/css`,
            {
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Read the HTML data of the given widget
     * 
     * @param viewId 
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public readHtml(viewId: string, observe?: 'body', reportProgress?: boolean): Observable<any>;
    public readHtml(viewId: string, observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<any>>;
    public readHtml(viewId: string, observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<any>>;
    public readHtml(viewId: string, observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        if (viewId === null || viewId === undefined) {
            throw new Error('Required parameter viewId was null or undefined when calling readHtml.');
        }

        let headers = this.defaultHeaders;

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'text/html'
        ];
        const httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set('Accept', httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        const consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<any>(`${this.basePath}/views/${encodeURIComponent(String(viewId))}/html`,
            {
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Read the Javascript data of the given widget
     * 
     * @param viewId 
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public readJavascript(viewId: string, observe?: 'body', reportProgress?: boolean): Observable<any>;
    public readJavascript(viewId: string, observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<any>>;
    public readJavascript(viewId: string, observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<any>>;
    public readJavascript(viewId: string, observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        if (viewId === null || viewId === undefined) {
            throw new Error('Required parameter viewId was null or undefined when calling readJavascript.');
        }

        let headers = this.defaultHeaders;

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/javascript'
        ];
        const httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set('Accept', httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        const consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<any>(`${this.basePath}/views/${encodeURIComponent(String(viewId))}/js`,
            {
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

}
