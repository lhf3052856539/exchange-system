// src/router/routes.js
const routes = [
    {
        path: '/',
        redirect: '/home'
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/auth/Login.vue'),
        meta: {
            title: '登录',
            requiresAuth: false
        }
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('@/views/auth/Register.vue'),
        meta: {
            title: '注册',
            requiresAuth: false
        }
    },
    {
        path: '/notifications',
        name: 'NotificationCenter',
        component: () => import('@/views/notification/NotificationCenter.vue'),
        meta: {
            title: '消息中心',
            requiresAuth: true
        }
    },
    {
        path: '/home',
        name: 'Home',
        component: () => import('@/views/home/Home.vue'),
        meta: {
            title: '首页',
            requiresAuth: true
        }
    },
    {
        path: '/airdrop',
        name: 'Airdrop',
        component: () => import('@/views/airdrop/AirdropPage.vue'),
        meta: {
            title: '空投领取',
            requiresAuth: true
        }
    },
    {
        path: '/trade',
        name: 'Trade',
        component: () => import('@/layouts/DefaultLayout.vue'),
        meta: {
            title: '交易中心',
            requiresAuth: true
        },
        children: [
            {
                path: 'request',
                name: 'TradeRequest',
                component: () => import('@/views/trade/RequestMatch.vue'),
                meta: {
                    title: '发起交易',
                    requiresAuth: true
                }
            },
            {
                path: 'list',
                name: 'TradeList',
                component: () => import('@/views/trade/TradeList.vue'),
                meta: {
                    title: '交易记录',
                    requiresAuth: true
                }
            },
            {
                path: ':id',
                name: 'TradeDetail',
                component: () => import('@/views/trade/TradeDetail.vue'),
                meta: {
                    title: '交易详情',
                    requiresAuth: true
                }
            }
        ]
    },
    {
        path: '/dao',
        name: 'Dao',
        component: () => import('@/views/dao/ProposalList.vue'),
        meta: {
            title: 'DAO治理',
            requiresAuth: true
        }
    },
    {
        path: '/dao/create',
        name: 'CreateProposal',
        component: () => import('@/views/dao/CreateProposal.vue'),
        meta: {
            title: '创建提案',
            requiresAuth: true
        }
    },
    {
        path: '/dao/:id',
        name: 'ProposalDetail',
        component: () => import('@/views/dao/ProposalDetail.vue'),
        meta: {
            title: '提案详情',
            requiresAuth: true
        }
    },
    {
        path: '/arbitration',
        name: 'Arbitration',
        component: () => import('@/views/arbitration/Committee.vue'),
        meta: {
            title: '仲裁委员会',
            requiresAuth: true
        }
    },
    {
        path: '/arbitration/proposal/:proposalId',
        name: 'ArbitrationProposalDetail',
        component: () => import('@/views/arbitration/ProposalDetail.vue'),
        meta: {
            title: '仲裁提案详情',
            requiresAuth: true
        }
    },
    {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/profile/Profile.vue'),
        meta: {
            title: '个人中心',
            requiresAuth: true
        },
        children: [
            {
                path: '',
                name: 'ProfileDefault',
                redirect: '/profile/assets'
            },
            {
                path: 'assets',
                name: 'ProfileAssets',
                component: () => import('@/views/profile/Assets.vue'),
                meta: {
                    title: '我的资产',
                    requiresAuth: true
                }
            },
            {
                path: 'settings',
                name: 'ProfileSettings',
                component: () => import('@/views/profile/Settings.vue'),
                meta: {
                    title: '设置',
                    requiresAuth: true
                }
            }
        ]
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/error/NotFound.vue'),
        meta: {
            title: '页面不存在'
        }
    }

]

export default routes
