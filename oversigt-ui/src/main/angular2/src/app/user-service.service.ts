import { Injectable } from '@angular/core';
import { AuthenticationService, Configuration } from 'src/oversigt-client';

const USER_NAME = 'user.name';
const USER_TOKEN = 'user.token';
const USER_ROLES = 'user.roles';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private __name: string = null;
  private __token: string = null;
  private __roles: string[] = [];

  private _requestedUrl: string = null;

  constructor(
    private authentication: AuthenticationService,
    private configuration: Configuration,
  ) {
    this.__name = localStorage.getItem(USER_NAME);
    this.__token = localStorage.getItem(USER_TOKEN);
    this.__roles = JSON.parse(localStorage.getItem(USER_ROLES));
    // TODO check if token is still valid - log out if not
  }

  isLoggedIn(): boolean {
    return this.token !== null && this.token !== '';
  }

  getName(): string {
    return this.name;
  }

  private get name(): string {
    return this.__name;
  }

  private set name(name: string) {
    this.__name = name;
    localStorage.setItem(USER_NAME, name);
  }

  private get token(): string {
    return this.__token;
  }

  private set token(token: string) {
    this.__token = token;
    localStorage.setItem(USER_TOKEN, token);
  }

  private get roles(): string[] {
    return this.__roles;
  }

  private set roles(roles: string[]) {
    this.__roles = roles;
    localStorage.setItem(USER_ROLES, JSON.stringify(roles));
  }

  get requestedUrl(): string {
    if (this._requestedUrl === null || this._requestedUrl.length === 0) {
      return '/';
    } else {
      return this._requestedUrl;
    }
  }

  set requestedUrl(url: string) {
    this._requestedUrl = url;
  }

  public logIn(username: string, password: string, success: (name: string) => void, fail: () => void, done: () => void): void {
    this.authentication.authenticateUser(username, password).subscribe(
      ok => {
        const authorization = 'Bearer ' + ok.token;
        this.token = ok.token;
        this.name = ok.displayName;
        this.roles = ok.roles;
        this.configuration.apiKeys['Authorization'] = authorization;
        success(ok.displayName);
      },
      error => {
        if (error.status === 403) {
          fail();
        } else {
          console.error(error);
          // TODO: Error handling
        }
      },
      done
    );
  }

  public logOut() {
    this.token = '';
    this.name = '';
    this.roles = [];
  }

  hasRole(roleName: string): boolean {
    return this.roles !== null && this.roles.includes(roleName);
  }
}
